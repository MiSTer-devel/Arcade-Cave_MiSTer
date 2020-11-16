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
-- File         : layer_processor.vhd
-- Description  :
--
-- Author       : Rick Wertenbroek
-- Version      : 0.0
--
-- VHDL std     : 2008
-- Dependencies : cave_pkg.vhd
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use work.cave_pkg.all;

entity layer_processor is
    port (
        -- Standard signals
        clk_i                     : in  std_logic;
        rst_i                     : in  std_logic;
        -- Control signals
        start_i                   : in  std_logic;
        done_o                    : out std_logic;
        -- Layer related info
        layer_number_i            : in  unsigned(1 downto 0);
        layer_info_i              : in  layer_info_line_t;
        -- Layer RAM interface (Do not delay ! It expects data the next cycle)
        layer_ram_addr_o          : out layer_ram_info_access_t;
        layer_ram_info_i          : in  layer_ram_line_t;
        -- Layer ROM interface
        layer_rom_addr_o          : out gfx_rom_addr_t;
        tiny_burst_o              : out std_logic;
        layer_burst_read_o        : out std_logic;
        layer_data_i              : in  gfx_rom_data_t;
        layer_data_valid_i        : in  std_logic;
        layer_data_burst_done_i   : in  std_logic;
        -- Priority RAM interface (Do not delay ! It expects data the next cycle)
        priority_ram_read_addr_o  : out priority_ram_addr_t;
        priority_ram_data_i       : in  priority_t;
        priority_ram_write_addr_o : out priority_ram_addr_t;
        priority_ram_data_o       : out priority_t;
        priority_ram_write_o      : out std_logic;
        -- Palette RAM interface (Do not delay ! It expects data the next cycle)
        palette_ram_addr_o        : out palette_ram_addr_t;
        palette_ram_data_i        : in  palette_ram_data_t;
        -- Frame Buffer interface
        frame_buffer_addr_o       : out frame_buffer_addr_t;
        frame_buffer_color_o      : out color_t;
        frame_buffer_write_o      : out std_logic
        );
end entity layer_processor;

architecture struct of layer_processor is

    -----------
    -- Types --
    -----------
    type state_t is (IDLE, REGISTER_INFO, SET_PARAMETERS, WAIT_BRAM, WORKING, WAIT_PIPELINE);

    -------------
    -- Signals --
    -------------
    signal state_reg_s, next_state_s        : state_t;
    --
    signal layer_info_s                     : layer_info_t;
    signal layer_info_reg_s                 : layer_info_t;
    signal tile_info_s                      : tile_info_t;
    signal tile_info_reg_s                  : tile_info_t;
    signal tile_data_burst_done_s           : std_logic;
    signal update_tile_info_s               : std_logic;
    signal tile_burst_read_s                : std_logic;
    -- TODO : Replace Magic
    signal work_done_s                      : std_logic;
    signal last_burst_s                     : std_logic;
    signal tile_info_taken_s                : std_logic;
    signal burst_todo_s                     : std_logic;
    signal burst_ready_s                    : std_logic;
    signal total_tile_counter_max_s         : unsigned(11 downto 0);
    signal start_x_pos_s                    : unsigned(8 downto 0);
    signal start_y_pos_s                    : unsigned(8 downto 0);
    -- TODO : Replace Magic
    signal tile_counter_max_x_s             : unsigned(5 downto 0);
    signal tile_counter_max_y_s             : unsigned(5 downto 0);
    signal tile_counter_x_s                 : unsigned(5 downto 0);
    signal tile_counter_y_s                 : unsigned(5 downto 0);
    signal tile_done_counter_s              : unsigned(11 downto 0);
    signal first_tile_index_s               : unsigned(11 downto 0);
    --
    -- Maybe put these signals in a block
    signal read_fifo_s                      : std_logic;
    signal data_to_pipeline_s               : std_logic_vector(63 downto 0);
    signal fifo_empty_s                     : std_logic;
    signal fifo_prog_full_s                 : std_logic;
    --
    signal pipeline_update_layer_info_reg_s : std_logic;
    signal last_layer_priority_reg_s        : priority_t;
    signal pipeline_takes_tile_info_s       : std_logic;
    signal palette_color_select_s           : palette_color_select_t;
    signal pipeline_done_writing_tile_s     : std_logic;
    --
    signal small_tile_index_s               : unsigned(11 downto 0);
    signal big_tile_index_s                 : unsigned(11 downto 0);
    --
    signal pipeline_reset_s                 : std_logic;

begin
    -- The layer info from the layer registers
    layer_info_s <= extract_global_layer_info_from_regs(layer_info_i);

    -- The tile info from the layer RAM
    tile_info_s <= extract_tile_info_from_layer_ram_line(layer_ram_info_i);

    -- Relabel for clarity
    tile_data_burst_done_s <= layer_data_burst_done_i;

    -- The work is done when all the tiles that could be on screen have been handled
    work_done_s <= '1' when (tile_done_counter_s = total_tile_counter_max_s) else '0';

    -- Update the tile information
    update_tile_info_s <= '1' when (state_reg_s = WORKING) and (tile_info_taken_s = '1') else '0';

    -- Issue a burst
    tile_burst_read_s <= '1' when (burst_todo_s = '1') and (burst_ready_s = '1') and (fifo_prog_full_s = '0') else '0';

    -- Indicates the last burst
    last_burst_s <= '1' when (tile_burst_read_s = '1') and (tile_counter_x_s = tile_counter_max_x_s) and (tile_counter_y_s = tile_counter_max_y_s) else '0';

    -- To compute the first tile index - TODO : This is not generic and will
    -- not work for all games, this should be adapted relative to the tile size
    -- parameter (+8 if small tiles).
    start_block : block
        signal magic_offset_x_s : unsigned(8 downto 0);
        signal magic_offset_y_s : unsigned(8 downto 0);
    begin

        -- "At rest" the offset in DDP is 0x195 for the first layer 0x195 = 405,
        -- 405 + 107 (0x6b) = 512 => 0.
        -- Due to pipeline pixel offsets this must be incremented by 1 each layer
        -- (and + 8 once if small tiles).
        magic_offset_x_s <= to_unsigned(16#6b#,         magic_offset_x_s'length) when layer_number_i = "00" else
                            to_unsigned(16#6b# + 1,     magic_offset_x_s'length) when layer_number_i = "01" else
                            to_unsigned(16#6b# + 2 + 8, magic_offset_x_s'length) when layer_number_i = "10" else
                            (others => '0');

        -- "At rest" the offset in DDP is 0x1EF = 495, 495 + 17 = 512 => 0
        magic_offset_y_s <= to_unsigned(17, magic_offset_y_s'length) when layer_number_i = "00" else
                            to_unsigned(17, magic_offset_y_s'length) when layer_number_i = "01" else
                            to_unsigned(17, magic_offset_y_s'length) when layer_number_i = "10" else
                            (others => '0');

        start_x_pos_s <= layer_info_reg_s.scroll_x + magic_offset_x_s;
        start_y_pos_s <= layer_info_reg_s.scroll_y + magic_offset_y_s;

    end block start_block;

    ---------------
    -- Registers --
    ---------------
    register_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if state_reg_s = IDLE then
                tile_info_taken_s <= '0';
                burst_todo_s      <= '0';
                burst_ready_s     <= '1';
            else

                if (state_reg_s = WAIT_BRAM) or (pipeline_takes_tile_info_s = '1') then
                    tile_info_taken_s <= '1';
                else
                    if update_tile_info_s = '1' then
                        tile_info_taken_s <= '0';
                    end if;
                end if; -- Tile info taken

                if update_tile_info_s = '1' then
                    burst_todo_s <= '1';
                else
                    if tile_burst_read_s = '1' then
                        burst_todo_s <= '0';
                    end if;
                end if; -- Burst todo

                if tile_burst_read_s = '1' then
                    burst_ready_s <= '0';
                else
                    if tile_data_burst_done_s = '1' then
                        burst_ready_s <= '1';
                    end if;
                end if; -- Burst ready
            end if; -- Reset

            if update_tile_info_s = '1' then
                tile_info_reg_s <= tile_info_s;
            end if;

            if state_reg_s = REGISTER_INFO then
                layer_info_reg_s <= layer_info_s;
                if layer_info_s.small_tile = '1' then
                    -- Small Tiles
                    total_tile_counter_max_s <= to_unsigned(41 * 31, total_tile_counter_max_s'length); -- TODO: Replace magic
                else
                    -- Big Tiles
                    total_tile_counter_max_s <= to_unsigned(21 * 16, total_tile_counter_max_s'length); -- TODO: Replace magic
                end if;
            end if; -- Register info

            if state_reg_s = SET_PARAMETERS then
                -- Set the base address etc.
                if layer_info_reg_s.small_tile = '1' then
                    first_tile_index_s <= start_x_pos_s(start_x_pos_s'high downto 3) + (start_y_pos_s(start_y_pos_s'high downto 3) & "000000");
                else
                    first_tile_index_s <= resize(start_x_pos_s(start_x_pos_s'high downto 4) + (start_y_pos_s(start_y_pos_s'high downto 4) & "00000"), first_tile_index_s'length);
                end if;

                -- TODO : Implement flip
            end if; -- Set parameters

        end if; -- Rising Edge Clock
    end process register_process;

    ---------
    -- FSM --
    ---------
    fsm_reg_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if rst_i = '1' then
                state_reg_s <= IDLE;
            else
                state_reg_s <= next_state_s;
            end if;
        end if;
    end process fsm_reg_process;

    fsm_next_state_process : process(all) is
    begin
        -- Default value, don't change
        next_state_s <= state_reg_s;

        case state_reg_s is

            when IDLE =>
                if start_i = '1' then
                    next_state_s <= REGISTER_INFO;
                end if;

            when REGISTER_INFO =>
                next_state_s <= SET_PARAMETERS;

            when SET_PARAMETERS =>
                if layer_info_reg_s.disabled = '1' then
                    next_state_s <= IDLE;
                else
                    next_state_s <= WAIT_BRAM;
                end if;

            when WAIT_BRAM =>
                next_state_s <= WORKING;

            when WORKING =>
                if last_burst_s = '1' then
                    next_state_s <= WAIT_PIPELINE;
                end if;

            when WAIT_PIPELINE =>
                if work_done_s = '1' then
                    next_state_s <= IDLE;
                end if;

        end case;
    end process fsm_next_state_process;

    --------------
    -- Counters --
    --------------

    tile_counter_max_x_s <= to_unsigned(DDP_VISIBLE_SCREEN_WIDTH/8, tile_counter_max_x_s'length) when layer_info_reg_s.small_tile = '1' else
                            to_unsigned(DDP_VISIBLE_SCREEN_WIDTH/16, tile_counter_max_x_s'length);
    tile_counter_max_y_s <= to_unsigned(DDP_VISIBLE_SCREEN_HEIGHT/8, tile_counter_max_y_s'length) when layer_info_reg_s.small_tile = '1' else
                            to_unsigned(DDP_VISIBLE_SCREEN_HEIGHT/16, tile_counter_max_y_s'length);

    -- Layer RAM tile counter
    tile_counter_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if state_reg_s = IDLE then
                tile_counter_x_s <= (others => '0');
                tile_counter_y_s <= (others => '0');
            else
                -- Update the counter when a burst is issued
                if tile_burst_read_s = '1' then
                    if tile_counter_x_s = tile_counter_max_x_s then
                        tile_counter_x_s <= (others => '0');
                        if tile_counter_y_s = tile_counter_max_y_s then
                            tile_counter_y_s <= (others => '0');
                        else
                            tile_counter_y_s <= tile_counter_y_s + 1;
                        end if; -- Update Y
                    else
                        tile_counter_x_s <= tile_counter_x_s + 1;
                    end if; -- Update X
                end if; -- Update
            end if; -- Reset
        end if; -- Rising Edge Clock
    end process tile_counter_process;

    -- Tile written by pipeline counter
    tile_done_counter_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if state_reg_s = IDLE then
                tile_done_counter_s <= (others => '0');
            else
                if pipeline_done_writing_tile_s = '1' then
                    tile_done_counter_s <= tile_done_counter_s + 1;
                end if; -- Update Counter
            end if; -- Reset Counter
        end if; -- Rising Edge Clock
    end process tile_done_counter_process;

    ----------
    -- FIFO --
    ----------
    tile_fifo_inst : entity work.tile_fifo
        port map (
            clock       => clk_i,
            data        => layer_data_i,
            rdreq       => read_fifo_s,
            wrreq       => layer_data_valid_i,
            almost_full => fifo_prog_full_s,
            empty       => fifo_empty_s,
            q           => data_to_pipeline_s);

    --------------------
    -- Layer Pipeline --
    --------------------
    pipeline_update_layer_info_reg_s <= '1' when state_reg_s = SET_PARAMETERS else
                                        '0';

    last_layer_priority_reg_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            -- If we are the first layer there is not last layer so priority
            -- should be 0
            if (state_reg_s = SET_PARAMETERS) and (layer_number_i = "00") then
                last_layer_priority_reg_s <= (others => '0');
            else
                -- If we finish doing our layer we can update this register
                if (state_reg_s = WORKING) and (work_done_s = '1') then
                    last_layer_priority_reg_s <= layer_info_reg_s.priority;
                end if;
            end if;
        end if;
    end process last_layer_priority_reg_process;

    pipeline_reset_s <= '1' when state_reg_s = IDLE else '0';

    layer_pipeline_inst : entity work.layer_pipeline
        port map (
            clk_i                     => clk_i,
            rst_i                     => pipeline_reset_s,
            update_layer_info_i       => pipeline_update_layer_info_reg_s,
            layer_number_i            => layer_number_i,
            layer_info_i              => layer_info_reg_s,
            last_layer_priority_i     => last_layer_priority_reg_s,
            tile_info_i               => tile_info_reg_s,
            get_tile_info_o           => pipeline_takes_tile_info_s,
            layer_burst_fifo_data_i   => data_to_pipeline_s,
            layer_burst_fifo_read_o   => read_fifo_s,
            layer_burst_fifo_empty_i  => fifo_empty_s,
            palette_color_select_o    => palette_color_select_s,
            palette_color_i           => extract_color_from_palette_data(palette_ram_data_i),
            priority_ram_read_addr_o  => priority_ram_read_addr_o,
            priority_ram_priority_i   => priority_ram_data_i,
            priority_ram_write_addr_o => priority_ram_write_addr_o,
            priority_ram_priority_o   => priority_ram_data_o,
            priority_ram_write_o      => priority_ram_write_o,
            frame_buffer_addr_o       => frame_buffer_addr_o,
            frame_buffer_color_o      => frame_buffer_color_o,
            frame_buffer_write_o      => frame_buffer_write_o,
            done_writing_tile_o       => pipeline_done_writing_tile_s);

    -------------
    -- Outputs --
    -------------

    -- The small tile need tiny bursts (they require half the data of the big
    -- tiles)
    tiny_burst_o <= '1' when layer_info_reg_s.small_tile = '1' else '0';

    layer_burst_read_o <= tile_burst_read_s;

    -- TODO : Rethink this (this is a quick fix to have a single clock cycle
    -- done pulse and it is delayed by one clock cycle to let the processor
    -- settle, this may not be necessary).
    process(clk_i)
    begin
        if rising_edge(clk_i) then
            if (state_reg_s /= IDLE) and (next_state_s = IDLE) then
                done_o <= '1';
            else
                done_o <= '0';
            end if;
        end if;
    end process;
    --done_o <= work_done_s;

    -- TODO : replace the two blocks below by a function
    small_tile_layer_ram_addr_block : block
        signal first_tile_index_sized_s : unsigned(11 downto 0);
        signal tile_x_offset_s          : unsigned(5 downto 0);
        signal tile_y_offset_s          : unsigned(5 downto 0);
    begin
        first_tile_index_sized_s <= resize(first_tile_index_s, first_tile_index_sized_s'length);

        tile_x_offset_s <= resize(first_tile_index_sized_s + tile_counter_x_s, tile_x_offset_s'length);
        tile_y_offset_s <= resize(first_tile_index_sized_s(first_tile_index_sized_s'high downto tile_x_offset_s'length) + tile_counter_y_s, tile_y_offset_s'length);

        small_tile_index_s <= tile_x_offset_s + (tile_y_offset_s & to_unsigned(0, tile_x_offset_s'length));

    end block small_tile_layer_ram_addr_block;

    big_tile_layer_ram_addr_block : block
        signal first_tile_index_sized_s : unsigned(9 downto 0);
        signal tile_x_offset_s          : unsigned(4 downto 0);
        signal tile_y_offset_s          : unsigned(4 downto 0);
    begin
        first_tile_index_sized_s <= resize(first_tile_index_s, first_tile_index_sized_s'length);

        tile_x_offset_s <= resize(first_tile_index_sized_s + tile_counter_x_s, tile_x_offset_s'length);
        tile_y_offset_s <= resize(first_tile_index_sized_s(first_tile_index_sized_s'high downto tile_x_offset_s'length) + tile_counter_y_s, tile_y_offset_s'length);

        big_tile_index_s <= resize(tile_x_offset_s + (tile_y_offset_s & to_unsigned(0, tile_x_offset_s'length)), big_tile_index_s'length);

    end block big_tile_layer_ram_addr_block;

    layer_ram_addr_o <= resize(small_tile_index_s, layer_ram_addr_o'length) when layer_info_reg_s.small_tile = '1' else
                        resize(big_tile_index_s, layer_ram_addr_o'length);

    layer_rom_addr_o <= resize(tile_info_reg_s.code * DDP_BYTES_PER_8x8_TILE, layer_rom_addr_o'length) when layer_info_reg_s.small_tile = '1' else
                        resize(tile_info_reg_s.code * DDP_BYTES_PER_16x16_TILE, layer_rom_addr_o'length);

    palette_ram_addr_o <= palette_ram_addr_from_palette_color_select(palette_color_select_s);

end struct;
