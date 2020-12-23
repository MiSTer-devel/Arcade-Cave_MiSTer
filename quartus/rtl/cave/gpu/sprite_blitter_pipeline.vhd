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
-- File         : sprite_blitter_pipeline.vhd
-- Description  : The sprite processor pipeline
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

entity sprite_blitter_pipeline is
    port (
        clk_i                     : in  std_logic;
        rst_i                     : in  std_logic;
        -- Sprite Info
        sprite_info_i             : in  sprite_ram_line_t;
        get_sprite_info_o         : out std_logic;
        -- Pixel data
        pixelData_bits            : in  std_logic_vector(63 downto 0);
        pixelData_ready           : out std_logic;
        pixelData_valid           : in  std_logic;
        -- Access to Palette RAM
        paletteRam_rd             : out std_logic;
        paletteRam_addr           : out palette_ram_addr_t;
        paletteRam_dout           : in  palette_ram_data_t;
        -- Access to Priority RAM
        priority_read_rd          : out std_logic;
        priority_read_addr        : out priority_ram_addr_t;
        priority_read_dout        : in  priority_t;
        priority_write_addr       : out priority_ram_addr_t;
        priority_write_din        : out priority_t;
        priority_write_wr         : out std_logic;
        -- Access to Frame Buffer
        frameBuffer_wr            : out std_logic;
        frameBuffer_addr          : out frame_buffer_addr_t;
        frameBuffer_mask          : out std_logic_vector(0 downto 0);
        frameBuffer_din           : out std_logic_vector(DDP_WORD_WIDTH-2 downto 0);
        -- Control signals
        done                      : out std_logic
        );
end entity sprite_blitter_pipeline;

architecture rtl of sprite_blitter_pipeline is

    -----------
    -- Types --
    -----------
    type piso_t is array (natural range <>) of code_16_colors_t;

    ---------------
    -- Constants --
    ---------------
    constant NUMBER_OF_COLOR_CODES_PER_FIFO_LINE : natural := (pixelData_bits'length)/(code_16_colors_t'length);

    -------------
    -- Signals --
    -------------
    signal sprite_info_reg_s         : sprite_info_t;
    signal update_sprite_info_s      : std_logic;

    signal counter_x_s               : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_X-1 downto 0);
    signal counter_y_s               : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_Y-1 downto 0);

    signal max_x_s                   : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_X-1 downto 0);
    signal max_y_s                   : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_Y-1 downto 0);

    signal read_fifo_s               : std_logic;

    signal piso_reg_s                : piso_t(NUMBER_OF_COLOR_CODES_PER_FIFO_LINE-1 downto 0);
    signal piso_counter_s            : unsigned(ilogup(piso_reg_s'length+1)-1 downto 0);
    signal piso_empty_s              : std_logic;
    signal piso_serial_out_s         : code_16_colors_t;

    signal frame_buffer_write_addr_s : frame_buffer_addr_t;
    signal update_frame_buffer_s     : std_logic;
    signal palette_ram_read_addr_s   : palette_color_select_t;
    signal priority_read_addr_s      : priority_ram_addr_t;

    -- Note :
    -- Stage 1 does :
    -- Read the palette and the priority
    -- Stage 2 does :
    -- Write the frame buffer and update priority
    signal stage_1_valid_s           : std_logic;
    signal stage_2_valid_s           : std_logic;
    signal sprite_done_s             : std_logic;
    signal stage_1_done_s            : std_logic;
    signal stage_2_done_s            : std_logic;

    signal old_priority_s            : priority_t;

    signal stage_1_current_prio_s    : priority_t;
    signal stage_2_current_prio_s    : priority_t;
    signal stage_0_pos_x_s           : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_X-1 downto 0);
    signal stage_1_pos_x_s           : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_X-1 downto 0);
    signal stage_2_pos_x_s           : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_X-1 downto 0);
    signal stage_0_pos_y_s           : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_Y downto 0);
    signal stage_1_pos_y_s           : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_Y downto 0);
    signal stage_2_pos_y_s           : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_Y downto 0);

    signal has_priority_s            : std_logic;
    signal is_transparent_s          : std_logic;
    signal visible_on_screen_s       : std_logic;

    signal frame_buffer_color_s      : color_t;

begin

    -- 2* since there are 2 colors per byte
    assert (pixelData_bits'length mod (2*code_16_colors_t'length)) = 0 report "Wrong Input FIFO width" severity error;

    -- If those don't meet timing, set them as registers and update at the same
    -- time as the sprite info reg and use sprite_info_i input.
    max_x_s <= resize(sprite_info_reg_s.tile_size_x * 16 - 1, max_x_s'length);
    max_y_s <= resize(sprite_info_reg_s.tile_size_y * 16 - 1, max_y_s'length);

    -- These reflect the position in the sprite (before flips)
    x_y_counters_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if rst_i = '1' then
                counter_x_s <= (others => '0');
                counter_y_s <= (others => '0');
            else
                if piso_empty_s = '0' then
                    if counter_x_s = max_x_s then
                        counter_x_s <= (others => '0');
                        if counter_y_s = max_y_s then
                            counter_y_s <= (others => '0');
                        else
                            counter_y_s <= counter_y_s + 1;
                        end if; -- Update Y
                    else
                        counter_x_s <= counter_x_s + 1;
                    end if; -- Update X
                end if; -- Update only when there is data
            end if; -- Reset
        end if; -- Rising Edge Clock
    end process x_y_counters_process;

    -- The FIFO can only be read when it is not empty and should be read if the
    -- piso is empty or will be empty next clock cycle (Since the pipeline
    -- after the FIFO has no backpressure and can accomodate data every clock
    -- cycle this will be the case if the piso counter is 1).
    read_fifo_s  <= '1' when (pixelData_valid = '1') and ((piso_empty_s = '1') or (piso_counter_s = 1)) else '0';

    piso_empty_s <= '1' when piso_counter_s = 0 else '0';

    piso_counter_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if rst_i = '1' then
                piso_counter_s <= (others => '0');
            else
                if read_fifo_s = '1' then
                    piso_counter_s <= to_unsigned(NUMBER_OF_COLOR_CODES_PER_FIFO_LINE, piso_counter_s'length);
                else
                    if piso_empty_s = '0' then
                        piso_counter_s <= piso_counter_s - 1;
                    end if; -- Update counter
                end if; -- Fill counter
            end if; -- Reset
        end if; -- Rising Edge Clock
    end process piso_counter_process;

    piso_process : process(clk_i) is
        variable data : std_logic_vector(63 downto 0) := pixelData_bits;
    begin
        if rising_edge(clk_i) then
            if read_fifo_s = '1' then
                piso_reg_s( 0) <= extract_nibble(data,  3);
                piso_reg_s( 1) <= extract_nibble(data,  2);
                piso_reg_s( 2) <= extract_nibble(data,  1);
                piso_reg_s( 3) <= extract_nibble(data,  0);
                piso_reg_s( 4) <= extract_nibble(data,  7);
                piso_reg_s( 5) <= extract_nibble(data,  6);
                piso_reg_s( 6) <= extract_nibble(data,  5);
                piso_reg_s( 7) <= extract_nibble(data,  4);
                piso_reg_s( 8) <= extract_nibble(data, 11);
                piso_reg_s( 9) <= extract_nibble(data, 10);
                piso_reg_s(10) <= extract_nibble(data,  9);
                piso_reg_s(11) <= extract_nibble(data,  8);
                piso_reg_s(12) <= extract_nibble(data, 15);
                piso_reg_s(13) <= extract_nibble(data, 14);
                piso_reg_s(14) <= extract_nibble(data, 13);
                piso_reg_s(15) <= extract_nibble(data, 12);
            else
                for i in 1 to piso_reg_s'length-1 loop
                    piso_reg_s(i-1) <= piso_reg_s(i);
                end loop; -- Shift the PISO
            end if; -- Parallel load
        end if; -- Rising Edge Clock
    end process piso_process;

    piso_serial_out_s <= piso_reg_s(0);

    sprite_info_reg_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if update_sprite_info_s = '1' then
                sprite_info_reg_s <= extract_sprite_info_from_sprite_ram_line(sprite_info_i);
            end if;
        end if;
    end process sprite_info_reg_process;

    -- The sprite info should be updated when we read a new sprite from the
    -- FIFO, this can be in either of two cases, first, when the counters are at
    -- 0 (no data yet) and the first pixels arrive, second, when a sprite
    -- finishes and the data for the second sprite is already there. This is
    -- to achieve maximum efficiency of the pipeline, while there are sprites
    -- to draw we burst them from memory into the pipeline. Max one 16x16 tile
    -- in advance, so there are at most 2 16x16 tiles in the pipeline at any
    -- given time. When there is space in the pipeline for a 16x16 tile and
    -- there are sprites to draw, a tile will be bursted from memory into the
    -- pipeline. A 16x16 tile is 16 bytes since a byte encodes 2 pixel colors
    -- on nibbles, these are 16 color sprites.
    update_sprite_info_s <= '1' when (read_fifo_s = '1') and (((counter_x_s = max_x_s) and (counter_y_s = max_y_s)) or ((counter_x_s = 0) and (counter_y_s = 0))) else '0';

    -- The sprite has been blitted when the counters are at max values and the
    -- piso is not empty.
    sprite_done_s <= '1' when (counter_x_s = max_x_s) and (counter_y_s = max_y_s) and (piso_empty_s = '0') else '0';

    -- Compute the flipped positions if needed
    stage_0_pos_x_s <= resize((sprite_info_reg_s.pos_x + counter_x_s), stage_0_pos_x_s'length) when sprite_info_reg_s.flip_x = '0' else
                       resize(((sprite_info_reg_s.pos_x - counter_x_s) + max_x_s), stage_0_pos_x_s'length);
    stage_0_pos_y_s <= resize((sprite_info_reg_s.pos_y + counter_y_s), stage_0_pos_y_s'length) when sprite_info_reg_s.flip_y = '0' else
                       resize(((sprite_info_reg_s.pos_y - counter_y_s) + max_y_s), stage_0_pos_y_s'length);

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
                stage_1_done_s  <= sprite_done_s;
                stage_2_done_s  <= stage_1_done_s;
            end if;

            -- The sprites use the 64 first palettes
            palette_ram_read_addr_s.palette <= "0" & sprite_info_reg_s.color_code;
            -- The sprites are 16 colors (out of 256 possible in palette).
            palette_ram_read_addr_s.color   <= "0000" & unsigned(piso_serial_out_s);
            stage_1_pos_x_s                 <= stage_0_pos_x_s;
            stage_2_pos_x_s                 <= stage_1_pos_x_s;
            stage_1_pos_y_s                 <= stage_0_pos_y_s;
            stage_2_pos_y_s                 <= stage_1_pos_y_s;
            stage_1_current_prio_s          <= sprite_info_reg_s.priority;
            stage_2_current_prio_s          <= stage_1_current_prio_s;
        end if;
    end process two_stage_pipeline_process;

    -- Addresses to BRAMs
    frame_buffer_write_addr_s <= stage_2_pos_x_s & stage_2_pos_y_s(DDP_FRAME_BUFFER_ADDR_BITS_Y-1 downto 0);
    priority_read_addr_s      <= stage_1_pos_x_s & stage_1_pos_y_s(DDP_FRAME_BUFFER_ADDR_BITS_Y-1 downto 0);

    -- Responses from BRAMs
    old_priority_s <= priority_read_dout;

    -- The current sprite has priority if it has more priority or the same
    -- priority as the previous sprite (all priority should be 0 at start)
    has_priority_s <= '1' when stage_2_current_prio_s >= old_priority_s else '0';

    -- Cave 1st Gen Hardware handles transparency the following way :
    -- if the color code (palette index) is 0 the pixel is transparent, this
    -- results in 15 usable colors for a tile. Even if the color at the first
    -- index of the palette is not zero the pixel still is transparent.
    -- It is difficult to understand why CAVE didn't use the MSB bit of the 16
    -- bit color word to indicate transparency. This would allow for 16 colors
    -- out of 2^15 colors for each tile instead of 15 colors of 2^15.
    -- With the cave CV1000 (SH3) hardware they use the MSB bit of the 16-bit
    -- word as transparency bit while the colors remain RGB555. One wonders why
    -- they didn't do this on 1st gen hardware.
    -- The transparency info must be delayed by one cycle, as for the colors
    -- (since the colors come from the palette RAM (BRAM) they arrive one cycle
    -- later).
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

    -- Write to Frame Buffer if :
    -- The data in stage 2 of the pipeline is valid
    -- The pixel has priority over the last pixel
    -- The pixel is not transparent
    -- The pixel is on screen
    update_frame_buffer_s <= stage_2_valid_s and has_priority_s and (not is_transparent_s) and visible_on_screen_s;

    frame_buffer_color_s <= extract_color_from_palette_data(paletteRam_dout);

    -------------
    -- Outputs --
    -------------
    get_sprite_info_o         <= update_sprite_info_s;
    pixelData_ready           <= read_fifo_s;
    paletteRam_rd             <= '1';
    paletteRam_addr           <= palette_ram_addr_from_palette_color_select(palette_ram_read_addr_s);
    priority_read_rd          <= '1';
    priority_read_addr        <= priority_read_addr_s;
    priority_write_addr       <= frame_buffer_write_addr_s;
    priority_write_din        <= stage_2_current_prio_s;
    priority_write_wr         <= update_frame_buffer_s;
    frameBuffer_wr            <= update_frame_buffer_s;
    frameBuffer_addr          <= frame_buffer_write_addr_s;
    frameBuffer_mask          <= "0";
    frameBuffer_din           <= frame_buffer_color_s.r & frame_buffer_color_s.g & frame_buffer_color_s.b;
    done                      <= stage_2_done_s;

end rtl;
