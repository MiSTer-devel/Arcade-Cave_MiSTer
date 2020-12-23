-------------------------------------------------------------------------------
--
-- Copyright (c) 2019 Rick Wertenbroek <rick.wertenbroek@gmail.com>
-- All rights reserved.
--
-- Redistribution and use in source and binary forms, with or without
-- modification, are permitted provided that the following conditions are met:
--
-- 1. Redistributions of source code must retain the above copyright notice,
-- this list of conditions and the following disclaimer.
--
-- 2. Redistributions in binary form must reproduce the above copyright notice,
-- this list of conditions and the following disclaimer in the documentation
-- and/or other materials provided with the distribution.
--
-- 3. Neither the name of the copyright holder nor the names of its
-- contributors may be used to endorse or promote products derived from this
-- software without specific prior written permission.
--
-- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
-- AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
-- IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
-- ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
-- LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
-- CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
-- SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
-- INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
-- CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
-- ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
-- POSSIBILITY OF SUCH DAMAGE.
--
-------------------------------------------------------------------------------
-- File         : layer_pipeline.vhd
-- Description  : The layer processor pipeline
--
-- Author       : Rick Wertenbroek
-- Version      : 0.0
--
-- VHDL std     : 2008
-- Dependencies : log_pkg.vhd, cave_pkg.vhd
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use work.log_pkg.all;
use work.cave_pkg.all;

entity layer_pipeline is
    port (
        clk_i                     : in  std_logic;
        rst_i                     : in  std_logic;
        -- Control (Layer Info)
        layer_number_i            : in  unsigned(1 downto 0);
        update_layer_info_i       : in  std_logic;
        layer_info_i              : in  layer_info_line_t;
        last_layer_priority_i     : in  priority_t;
        -- Tile Info
        tile_info_i               : in  layer_ram_line_t;
        get_tile_info_o           : out std_logic;
        -- Burst FIFO
        layer_burst_fifo_data_i   : in  std_logic_vector(63 downto 0);
        layer_burst_fifo_read_o   : out std_logic;
        layer_burst_fifo_empty_i  : in  std_logic;
        -- Access to Palette RAM
        palette_color_select_o    : out palette_ram_addr_t;
        -- Access to Priority RAM
        priority_read_rd          : out std_logic;
        priority_read_addr        : out priority_ram_addr_t;
        priority_read_dout        : in  priority_t;
        priority_write_addr       : out priority_ram_addr_t;
        priority_write_din        : out priority_t;
        priority_write_wr         : out std_logic;
        -- Access to Frame Buffer
        frame_buffer_addr_o       : out frame_buffer_addr_t;
        frame_buffer_write_o      : out std_logic;
        -- Control signals
        done_writing_tile_o       : out std_logic
        );
end entity layer_pipeline;

architecture rtl of layer_pipeline is

    -----------
    -- Types --
    -----------
    type piso_16_t  is array (natural range <>) of code_16_colors_t;
    type piso_256_t is array (natural range <>) of code_256_colors_t;

    ---------------
    -- Constants --
    ---------------
    constant NUMBER_OF_COLOR_CODES_PER_FIFO_LINE            : natural := (layer_burst_fifo_data_i'length)/(code_16_colors_t'length);
    constant NUMBER_OF_COLOR_CODES_PER_FIFO_LINE_SMALL_TILE : natural := (layer_burst_fifo_data_i'length)/(code_256_colors_t'length);
    constant MAX_X_C : natural := 8-1;
    constant MAX_Y_C : natural := 8-1;

    -------------
    -- Signals --
    -------------
    -- TODO : Replace all the magic numbers
    signal counter_x_s               : unsigned(2 downto 0);
    signal counter_y_s               : unsigned(2 downto 0);
    --
    signal mini_tile_counter_x_s     : unsigned(0 downto 0);
    signal mini_tile_counter_y_s     : unsigned(0 downto 0);
    signal tile_counter_x_s          : unsigned(5 downto 0);
    signal tile_counter_y_s          : unsigned(5 downto 0);
    --
    signal read_fifo_s               : std_logic;
    signal piso_empty_s              : std_logic;
    signal piso_counter_s            : unsigned(ilogup(NUMBER_OF_COLOR_CODES_PER_FIFO_LINE+1)-1 downto 0);
    --
    signal update_small_tile_info_s  : std_logic;
    signal piso_small_reg_s          : piso_256_t(NUMBER_OF_COLOR_CODES_PER_FIFO_LINE_SMALL_TILE-1 downto 0);
    signal update_big_tile_info_s    : std_logic;
    signal piso_big_reg_s            : piso_16_t(NUMBER_OF_COLOR_CODES_PER_FIFO_LINE-1 downto 0);
    --
    signal update_tile_info_s        : std_logic;
    signal tile_info_reg_s           : tile_info_t;
    --
    signal update_layer_info_s       : std_logic;
    signal layer_info_reg_s          : layer_info_t;
    --
    signal tile_done_s               : std_logic;
    signal small_tile_done_s         : std_logic;
    signal big_tile_done_s           : std_logic;
    --
    signal tile_position_x_s         : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_X-1 downto 0);
    signal tile_position_y_s         : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_Y downto 0);
    signal offset_x_s                : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_X-1 downto 0);
    signal offset_y_s                : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_Y downto 0);
    --
    signal stage_0_pos_x_s           : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_X-1 downto 0);
    signal stage_1_pos_x_s           : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_X-1 downto 0);
    signal stage_2_pos_x_s           : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_X-1 downto 0);
    signal stage_0_pos_y_s           : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_Y downto 0);
    signal stage_1_pos_y_s           : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_Y downto 0);
    signal stage_2_pos_y_s           : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_Y downto 0);
    signal stage_0_color_index_s     : unsigned(DDP_PALETTE_COLOR_SELECT_BITS-1 downto 0);
    signal stage_1_valid_s           : std_logic;
    signal stage_2_valid_s           : std_logic;
    signal stage_1_done_s            : std_logic;
    signal stage_2_done_s            : std_logic;
    signal stage_1_current_prio_s    : priority_t;
    signal stage_2_current_prio_s    : priority_t;
    --
    signal palette_ram_read_addr_s   : palette_color_select_t;
    signal frame_buffer_write_addr_s : frame_buffer_addr_t;
    signal priority_read_addr_s      : priority_ram_addr_t;
    signal old_priority_s            : priority_t;
    signal has_priority_s            : std_logic;
    signal is_transparent_s          : std_logic;
    signal visible_on_screen_s       : std_logic;
    signal update_frame_buffer_s     : std_logic;

begin

    -- Counters to go through the small tiles (8x8)
    x_y_counters_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if rst_i = '1' then
                counter_x_s <= (others => '0');
                counter_y_s <= (others => '0');
            else
                -- Update only if there is data
                if piso_empty_s = '0' then
                    if counter_x_s = MAX_X_C then
                        counter_x_s <= (others => '0');
                        if counter_y_s = MAX_Y_C then
                            counter_y_s <= (others => '0');
                        else
                            counter_y_s <= counter_y_s + 1;
                        end if; -- Update Y
                    else
                        counter_x_s <= counter_x_s + 1;
                    end if; -- Update X
                end if; -- Update only if there is data
            end if; -- Reset
        end if; -- Rising Edge Clock
    end process x_y_counters_process;

    mini_tile_counters_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if rst_i = '1' then
                mini_tile_counter_x_s <= (others => '0');
                mini_tile_counter_y_s <= (others => '0');
            else
                -- Update only if there is data
                if piso_empty_s = '0' then
                    -- Update only if mini tile finished drawing
                    if (counter_x_s = MAX_X_C) and (counter_y_s = MAX_Y_C) then
                        if mini_tile_counter_x_s = 1 then
                            mini_tile_counter_x_s <= (others => '0');
                            if mini_tile_counter_y_s = 1 then
                                mini_tile_counter_y_s <= (others => '0');
                            else
                                mini_tile_counter_y_s <= mini_tile_counter_y_s + 1;
                            end if; -- Update mini tile Y
                        else
                            mini_tile_counter_x_s <= mini_tile_counter_x_s + 1;
                        end if; -- Update mini tile X
                    end if; -- Update only if mini tile finished
                end if; -- Update only if there is data
            end if; -- Reset
        end if; -- Rising Edge Clock
    end process mini_tile_counters_process;

    screen_tile_counter_block : block
        signal max_tile_x_s                  : unsigned(5 downto 0);
        signal max_tile_y_s                  : unsigned(5 downto 0);
        --
        signal counter_xy_at_max_s           : std_logic;
        signal mini_tile_counter_xy_at_max_s : std_logic;
        signal update_screen_tile_counter_s  : std_logic;
    begin

        counter_xy_at_max_s <= '1' when (counter_x_s = MAX_X_C) and (counter_y_s = MAX_Y_C) else '0';
        mini_tile_counter_xy_at_max_s <= '1' when (mini_tile_counter_x_s = 1) and (mini_tile_counter_y_s = 1) else '0';
        update_screen_tile_counter_s <= '0' when counter_xy_at_max_s = '0' else
                                        '1' when layer_info_reg_s.small_tile = '1' else
                                        '1' when mini_tile_counter_xy_at_max_s = '1' else
                                        '0';

        -- Edge conditions
        -- TODO : replace magic numbers
        max_tile_x_s <= to_unsigned(DDP_VISIBLE_SCREEN_WIDTH/8, max_tile_x_s'length) when layer_info_reg_s.small_tile = '1' else
                        to_unsigned(DDP_VISIBLE_SCREEN_WIDTH/16, max_tile_x_s'length);
        max_tile_y_s <= to_unsigned(DDP_VISIBLE_SCREEN_HEIGHT/8, max_tile_y_s'length) when layer_info_reg_s.small_tile = '1' else
                        to_unsigned(DDP_VISIBLE_SCREEN_HEIGHT/16, max_tile_y_s'length);

        -- These count the tiles to be drawn on the 320x240 screen, there is an
        -- extra tile for when the screen boundary is not aligned with the first
        -- tile du to layer offsets.
        screen_tile_counters_process : process(clk_i) is
        begin
            if rising_edge(clk_i) then
                if rst_i = '1' then
                    tile_counter_x_s <= (others => '0');
                    tile_counter_y_s <= (others => '0');
                else
                    if update_screen_tile_counter_s = '1' then
                        if tile_counter_x_s = max_tile_x_s then
                            tile_counter_x_s <= (others => '0');
                            if tile_counter_y_s = max_tile_y_s then
                                tile_counter_y_s <= (others => '0');
                            else
                                tile_counter_y_s <= tile_counter_y_s + 1;
                            end if; -- Update tile counter Y
                        else
                            tile_counter_x_s <= tile_counter_x_s + 1;
                        end if; -- Update tile counter X
                    end if; -- Update
                end if; -- Reset
            end if; -- Rising Edge Clock
        end process screen_tile_counters_process;

    end block screen_tile_counter_block;

    -- The FIFO should only be read when it is not empty and should be read if
    -- the PISO is empty or will be empty next clock cycle (Since the pipeline
    -- after the FIFO has no backpressure and can accomodate data every clock
    -- cycle this will be the case if the piso counter is 1).
    read_fifo_s  <= '1' when (layer_burst_fifo_empty_i = '0') and ((piso_empty_s = '1') or (piso_counter_s = 1)) else '0';

    piso_empty_s <= '1' when piso_counter_s = 0 else '0';

    piso_counter_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if rst_i = '1' then
                piso_counter_s <= (others => '0');
            else
                if read_fifo_s = '1' then
                    if layer_info_reg_s.small_tile = '1' then
                        piso_counter_s <= to_unsigned(NUMBER_OF_COLOR_CODES_PER_FIFO_LINE_SMALL_TILE, piso_counter_s'length);
                    else
                        piso_counter_s <= to_unsigned(NUMBER_OF_COLOR_CODES_PER_FIFO_LINE, piso_counter_s'length);
                    end if; -- tile size
                else
                    if piso_empty_s = '0' then
                        piso_counter_s <= piso_counter_s - 1;
                    end if; -- Update counter
                end if; -- Fill counter
            end if; -- Reset
        end if; -- Rising Edge Clock
    end process piso_counter_process;

    -- 256 Color codes
    piso_small_tile_process : process(clk_i) is
        variable data : std_logic_vector(63 downto 0) := layer_burst_fifo_data_i;
    begin
        if rising_edge(clk_i) then
            if read_fifo_s = '1' then
                piso_small_reg_s(0) <= extract_nibble(data,  3) & extract_nibble(data,  1);
                piso_small_reg_s(1) <= extract_nibble(data,  2) & extract_nibble(data,  0);
                piso_small_reg_s(2) <= extract_nibble(data,  7) & extract_nibble(data,  5);
                piso_small_reg_s(3) <= extract_nibble(data,  6) & extract_nibble(data,  4);
                piso_small_reg_s(4) <= extract_nibble(data, 11) & extract_nibble(data,  9);
                piso_small_reg_s(5) <= extract_nibble(data, 10) & extract_nibble(data,  8);
                piso_small_reg_s(6) <= extract_nibble(data, 15) & extract_nibble(data, 13);
                piso_small_reg_s(7) <= extract_nibble(data, 14) & extract_nibble(data, 12);
            else
                -- Shift the PISO
                for i in 1 to piso_small_reg_s'length-1 loop
                    piso_small_reg_s(i-1) <= piso_small_reg_s(i);
                end loop; -- Shift the PISO
            end if; -- Parallel load
        end if; -- Rising Edge Clock
    end process piso_small_tile_process;

    -- 16 Color codes
    piso_big_tile_process : process(clk_i) is
        variable data : std_logic_vector(63 downto 0) := layer_burst_fifo_data_i;
    begin
        if rising_edge(clk_i) then
            if read_fifo_s = '1' then
                piso_big_reg_s( 0) <= extract_nibble(data,  1);
                piso_big_reg_s( 1) <= extract_nibble(data,  0);
                piso_big_reg_s( 2) <= extract_nibble(data,  3);
                piso_big_reg_s( 3) <= extract_nibble(data,  2);
                piso_big_reg_s( 4) <= extract_nibble(data,  5);
                piso_big_reg_s( 5) <= extract_nibble(data,  4);
                piso_big_reg_s( 6) <= extract_nibble(data,  7);
                piso_big_reg_s( 7) <= extract_nibble(data,  6);
                piso_big_reg_s( 8) <= extract_nibble(data,  9);
                piso_big_reg_s( 9) <= extract_nibble(data,  8);
                piso_big_reg_s(10) <= extract_nibble(data, 11);
                piso_big_reg_s(11) <= extract_nibble(data, 10);
                piso_big_reg_s(12) <= extract_nibble(data, 13);
                piso_big_reg_s(13) <= extract_nibble(data, 12);
                piso_big_reg_s(14) <= extract_nibble(data, 15);
                piso_big_reg_s(15) <= extract_nibble(data, 14);
            else
                -- Shift the PISO
                for i in 1 to piso_big_reg_s'length-1 loop
                    piso_big_reg_s(i-1) <= piso_big_reg_s(i);
                end loop; -- Shift the PISO
            end if; -- Parallel load
        end if; -- Rising Edge Clock
    end process piso_big_tile_process;

    tile_info_reg_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if update_tile_info_s = '1' then
                tile_info_reg_s <= extract_tile_info_from_layer_ram_line(tile_info_i);
            end if; -- Load
        end if; -- Rising Edge Clock
    end process tile_info_reg_process;

    update_layer_info_s <= update_layer_info_i;

    layer_info_reg_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if update_layer_info_s = '1' then
                layer_info_reg_s <= extract_global_layer_info_from_regs(layer_info_i);
            end if; -- Load
        end if;
    end process layer_info_reg_process;

    -- The tile info should be updated when we read a new tile from the FIFO,
    -- this can be in either two cases, first, when the counters are at 0 (no
    -- data yet) and the first pixels arrive, second, when a tile finishes and
    -- the data for the next tile is already ready. (This is to achieve maximum
    -- efficiency of the pipeline, while there are tiles to draw we burst them
    -- from memory into the pipeline.

    -- Note : These conditions can be simplified. (I hope the synthesizer will
    -- do its job and optimize this).
    update_tile_info_s <= update_small_tile_info_s when layer_info_reg_s.small_tile = '1' else
                          update_big_tile_info_s;

    update_small_tile_info_s <= '1' when (read_fifo_s = '1') and (((counter_x_s = MAX_X_C) and (counter_y_s = MAX_Y_C)) or ((counter_x_s = 0) and (counter_y_s = 0))) else '0';

    update_big_tile_info_s   <= '1' when (read_fifo_s = '1') and ((((counter_x_s = MAX_X_C) and (mini_tile_counter_x_s = 1)) and ((counter_y_s = MAX_Y_C) and (mini_tile_counter_y_s = 1))) or (((counter_x_s = 0) and (mini_tile_counter_x_s = 0)) and ((counter_y_s = 0) and (mini_tile_counter_y_s = 0)))) else '0';

    tile_done_s <= small_tile_done_s when layer_info_reg_s.small_tile = '1' else big_tile_done_s;

    small_tile_done_s <= '1' when (counter_x_s = MAX_X_C) and (counter_y_s = MAX_Y_C) and (piso_empty_s = '0') else '0';

    big_tile_done_s <= '1' when (small_tile_done_s = '1') and (mini_tile_counter_x_s = 1) and (mini_tile_counter_y_s = 1) else '0';

    -- 8x8 or 16x16 tiles - TODO : Remove magic numbers
    tile_position_x_s <= resize((tile_counter_x_s & "000"), tile_position_x_s'length) when layer_info_reg_s.small_tile = '1' else
                         resize((tile_counter_x_s & "0000") + (mini_tile_counter_x_s & "000"), tile_position_x_s'length);
    tile_position_y_s <= resize((tile_counter_y_s & "000"), tile_position_y_s'length) when layer_info_reg_s.small_tile = '1' else
                         resize((tile_counter_y_s & "0000") + (mini_tile_counter_y_s & "000"), tile_position_y_s'length);

    offset_block : block
        signal magic_offset_x_s : unsigned(3 downto 0);
        signal magic_offset_y_s : unsigned(3 downto 0);
    begin

        -- Note : Truncation is normal here (Since we only need the internal tile offset).
        magic_offset_x_s <= to_unsigned(16#6b#,         magic_offset_x_s'length) when layer_number_i = "00" else
                            to_unsigned(16#6b# + 1,     magic_offset_x_s'length) when layer_number_i = "01" else
                            to_unsigned(16#6b# + 2 + 8, magic_offset_x_s'length) when layer_number_i = "10" else
                            (others => '0');

        -- "At rest" the offset in DDP is 0x1EF = 495, 495 + 17 = 512 => 0
        magic_offset_y_s <= to_unsigned(17, magic_offset_y_s'length) when layer_number_i = "00" else
                            to_unsigned(17, magic_offset_y_s'length) when layer_number_i = "01" else
                            to_unsigned(17, magic_offset_y_s'length) when layer_number_i = "10" else
                            (others => '0');

        offset_x_s <= 0 - resize(resize(layer_info_reg_s.scroll_x(2 downto 0) + magic_offset_x_s, 3), offset_x_s'length) when layer_info_reg_s.small_tile = '1' else
                      0 - resize(resize(layer_info_reg_s.scroll_x(3 downto 0) + magic_offset_x_s, 4), offset_x_s'length);

        offset_y_s <= 0 - resize(resize(layer_info_reg_s.scroll_y(2 downto 0) + magic_offset_y_s, 3), offset_y_s'length) when layer_info_reg_s.small_tile = '1' else
                      0 - resize(resize(layer_info_reg_s.scroll_y(3 downto 0) + magic_offset_y_s, 4), offset_y_s'length);

    end block offset_block;

    -- TODO : Implement layer flip (not yet done)
    stage_0_pos_x_s <= resize(tile_position_x_s + counter_x_s + offset_x_s, stage_0_pos_x_s'length);
    stage_0_pos_y_s <= resize(tile_position_y_s + counter_y_s + offset_y_s, stage_0_pos_y_s'length);

    stage_0_color_index_s <= resize(unsigned(piso_small_reg_s(0)), stage_0_color_index_s'length) when layer_info_reg_s.small_tile = '1' else
                             resize(unsigned(piso_big_reg_s(0)), stage_0_color_index_s'length);

    two_stage_pipeline_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then

            -- Validity registers
            if rst_i = '1' then
                stage_1_valid_s <= '0';
                stage_2_valid_s <= '0';
                stage_1_done_s  <= '0';
                stage_2_done_s  <= '0';
            else
                stage_1_valid_s <= not piso_empty_s;
                stage_2_valid_s <= stage_1_valid_s;
                stage_1_done_s  <= tile_done_s;
                stage_2_done_s  <= stage_1_done_s;
            end if;

            -- The tiles use the 64 next palettes
            palette_ram_read_addr_s.palette <= "1" & tile_info_reg_s.color_code;
            palette_ram_read_addr_s.color   <= stage_0_color_index_s;
            stage_1_pos_x_s                 <= stage_0_pos_x_s;
            stage_2_pos_x_s                 <= stage_1_pos_x_s;
            stage_1_pos_y_s                 <= stage_0_pos_y_s;
            stage_2_pos_y_s                 <= stage_1_pos_y_s;
            stage_1_current_prio_s          <= tile_info_reg_s.priority;
            stage_2_current_prio_s          <= stage_1_current_prio_s;
        end if;
    end process two_stage_pipeline_process;

    -- The current tile pixel had priority if it has more priority as the
    -- previous pixel, otherwhise if their priority is the same it depends on
    -- the layer priorities.
    has_priority_s <= '1' when stage_2_current_prio_s > old_priority_s else
                      '1' when (stage_2_current_prio_s = old_priority_s) and (layer_info_reg_s.priority >= last_layer_priority_i) else
                      '0';

    -- Transparency, if the first color from the palette is picked it means the
    -- pixel is transparent (and does not depend on the actual color of the
    -- palette at the first address). Delayed by one cycle to arrive at the
    -- same time as the color would.
    process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if (palette_ram_read_addr_s.color = 0) then
                is_transparent_s <= '1';
            else
                is_transparent_s <= '0';
            end if;
        end if;
    end process;

    -- The tile pixel should be written to FB when it is visible
    visible_on_screen_s <= '1' when (stage_2_pos_x_s < DDP_VISIBLE_SCREEN_WIDTH) and (stage_2_pos_y_s < DDP_VISIBLE_SCREEN_HEIGHT) else '0';

    -- Write to frame buffer if :
    -- The data in stage 2 of the pipeline is valid
    -- The pixel has priority over the last pixel at that position
    -- The pixel is not transparent
    -- The pixel is on screen
    update_frame_buffer_s <= stage_2_valid_s and has_priority_s and (not is_transparent_s) and visible_on_screen_s;
    -- Note : there is some overlap on the conditions above (transparency) but
    -- the synthesizer should optimize that out hopefully

    -- Addresses to BRAMs
    frame_buffer_write_addr_s <= stage_2_pos_x_s & stage_2_pos_y_s(DDP_FRAME_BUFFER_ADDR_BITS_Y-1 downto 0);
    priority_read_addr_s      <= stage_1_pos_x_s & stage_1_pos_y_s(DDP_FRAME_BUFFER_ADDR_BITS_Y-1 downto 0);

    -- Responses from BRAMs
    old_priority_s <= priority_read_dout;

    -------------
    -- Outputs --
    -------------
    get_tile_info_o           <= update_tile_info_s;
    layer_burst_fifo_read_o   <= read_fifo_s;
    palette_color_select_o    <= palette_ram_addr_from_palette_color_select(palette_ram_read_addr_s);
    priority_read_rd          <= '1';
    priority_read_addr        <= priority_read_addr_s;
    priority_write_addr       <= frame_buffer_write_addr_s;
    priority_write_din        <= stage_2_current_prio_s;
    priority_write_wr         <= update_frame_buffer_s;
    frame_buffer_addr_o       <= frame_buffer_write_addr_s;
    frame_buffer_write_o      <= update_frame_buffer_s;
    done_writing_tile_o       <= stage_2_done_s;

end rtl;
