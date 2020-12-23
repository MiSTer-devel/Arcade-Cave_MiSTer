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
-- File         : sprite_processor.vhd
-- Description  : This sprite processor will search all 1024 positions in the
--                sprite RAM according to the selected buffer to see if there
--                are sprites. Each sprite found will be processed by a
--                pipeline comprised of a stage that will burst read the sprite
--                ROM followed by a FIFO followed by the sprite blitter
--                pipeline. When all 1024 sprite locations have been inspected
--                and all found sprites have been blitted the processor will
--                return in an IDLE state (not busy).
--
-- Author       : Rick Wertenbroek
-- Version      : 0.1
--
-- VHDL std     : 2008
-- Dependencies : cave_pkg.vhd
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use work.log_pkg.all;
use work.cave_pkg.all;

entity sprite_processor is
    port (
        -- Standard signals
        clk_i                     : in  std_logic;
        rst_i                     : in  std_logic;
        -- Control signals
        start_i                   : in  std_logic; -- Start drawing sprites
        done_o                    : out std_logic;
        sprite_bank_i             : in  std_logic; -- Which sprites to draw
        -- Sprite RAM interface (Do not delay ! It expects data the next cycle)
        spriteRam_rd              : out std_logic;
        spriteRam_addr            : out sprite_ram_info_access_t;
        spriteRam_dout            : in  sprite_ram_line_t;
        -- Sprite ROM interface
        tileRom_rd                : out std_logic;
        tileRom_addr              : out gfx_rom_addr_t;
        tileRom_dout              : in  gfx_rom_data_t;
        tileRom_waitReq           : in  std_logic;
        tileRom_valid             : in  std_logic;
        tileRom_burstLength       : out std_logic_vector(7 downto 0);
        tileRom_burstDone         : in  std_logic; -- Asserted when last word arrives
        -- Priority RAM interface (Do not delay ! It expects data the next cycle)
        priority_read_rd          : out std_logic;
        priority_read_addr        : out priority_ram_addr_t;
        priority_read_dout        : in  priority_t;
        priority_write_wr         : out std_logic;
        priority_write_addr       : out priority_ram_addr_t;
        priority_write_din        : out priority_t;
        -- Palette RAM interface (Do not delay ! It expects data the next cycle)
        paletteRam_rd             : out std_logic;
        paletteRam_addr           : out palette_ram_addr_t;
        paletteRam_dout           : in  palette_ram_data_t;
        -- Frame Buffer interface
        frameBuffer_addr          : out frame_buffer_addr_t;
        frameBuffer_mask          : out std_logic_vector(0 downto 0);
        frameBuffer_din           : out std_logic_vector(DDP_WORD_WIDTH-2 downto 0);
        frameBuffer_wr            : out std_logic
        );
end entity sprite_processor;

architecture struct of sprite_processor is

    ---------------
    -- Constants --
    ---------------

    -- This value is found in the sprite RAM at pos_x or pos_y when there is no
    -- sprite meant to be drawn
    constant NO_SPRITE_MAGIC_POS : natural := 16#2A0#;

    -----------
    -- Types --
    -----------
    type state_t is (IDLE, BEGIN_CHECK, WORKING);

    -------------
    -- Signals --
    -------------
    signal state_reg_s                  : state_t;
    signal next_state_s                 : state_t;
    signal work_done_s                  : std_logic;

    signal sprite_info_s                : sprite_info_t;
    signal sprite_info_reg_s            : sprite_info_t;
    signal raw_sprite_info_reg_s        : sprite_ram_line_t;
    signal update_sprite_info_s         : std_logic;
    signal sprite_in_ram_line_s         : std_logic;
    signal burst_todo_s                 : std_logic;
    signal sprite_info_taken_s          : std_logic;
    signal burst_counter_s              : unsigned(sprite_info_s.tile_size_x'length+sprite_info_s.tile_size_y'length-1 downto 0);
    signal burst_counter_max_s          : unsigned(burst_counter_s'length-1 downto 0);
    signal done_bursting_s              : std_logic;
    signal sprite_burst_read_s          : std_logic;
    signal effective_read_s             : std_logic;
    signal burst_ready_s                : std_logic;

    signal read_fifo_s                  : std_logic;
    signal data_to_pipeline_s           : gfx_rom_data_t;
    signal fifo_empty_s                 : std_logic;
    signal fifo_prog_full_s             : std_logic;
    signal pipeline_takes_sprite_info_s : std_logic;
    signal pipeline_done_blitting_s     : std_logic;

    -- These counters will wrap around if 1024 sprites are processed, however
    -- this is not a problem and the whole pipeline still works, it will just
    -- check if both counters are 0 to terminate. I don't think there is a
    -- screen with 1024 sprites in Dodonpachi so no worries, but this can be
    -- verified in a test bench.
    signal sent_sprite_counter_s        : unsigned(ilogup(DDP_MAX_SPRITES_ON_SCREEN)-1 downto 0);
    signal done_sprite_counter_s        : unsigned(ilogup(DDP_MAX_SPRITES_ON_SCREEN)-1 downto 0);
    signal reset_sprite_counters_s      : std_logic;

    -- This one will go to 1024 each time and we use the MSB bit as a control signal
    signal sprite_info_counter_s        : unsigned(ilogup(DDP_MAX_SPRITES_ON_SCREEN) downto 0);
    signal next_sprite_info_s           : std_logic;
begin

    -- The sprite info from the sprite RAM
    sprite_info_s <= extract_sprite_info_from_sprite_ram_line(spriteRam_dout);

    -- There is no sprite in the ram line if the magic is in pos_x or if one of
    -- the tile sizes is 0.
    sprite_in_ram_line_s <= '0' when (sprite_info_s.pos_x = NO_SPRITE_MAGIC_POS) or (sprite_info_s.tile_size_x = 0) or (sprite_info_s.tile_size_y = 0) else '1';

    -- Check the next sprite ram info (only when in working state) and either
    -- there is no sprite in memory at that location or the sprite has been
    -- sent down the pipeline.
    next_sprite_info_s <= '1' when (state_reg_s = WORKING) and (sprite_info_counter_s(10) = '0') and ((sprite_in_ram_line_s = '0') or (update_sprite_info_s = '1')) else '0';

    -- The work is done when all 1024 sprite locations have been checked and
    -- all sprites found have been blitted.
    work_done_s <= '1' when (sprite_info_counter_s(10) = '1') and (sent_sprite_counter_s = done_sprite_counter_s) else '0';

    -- Update the sprite info when working, and there is a sprite to do, and
    -- the last info has been taken by the blitter, and the burst todo flag is low.
    update_sprite_info_s <= '1' when (state_reg_s = WORKING) and (sprite_info_counter_s(10) = '0') and (sprite_in_ram_line_s = '1') and (sprite_info_taken_s = '1') and (burst_todo_s = '0') else '0';

    -- Bursting is done when there was a burst to do and the burst counter
    -- reached the required maximum
    done_bursting_s <= '1' when (burst_todo_s = '1') and (burst_counter_s = burst_counter_max_s) else '0';

    -- Issue a burst when ready and there is burst todo and the FIFO is ready
    -- for some feisty bursty data.
    sprite_burst_read_s <= '1' when (burst_todo_s = '1') and (burst_ready_s = '1') and (fifo_prog_full_s = '0') else '0';
    effective_read_s <= '1' when sprite_burst_read_s = '1' and tileRom_waitReq = '0' else '0';

    register_processs : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if state_reg_s = IDLE then
                sprite_info_taken_s <= '1';
                burst_todo_s        <= '0';
                burst_ready_s       <= '1';
            else
                if pipeline_takes_sprite_info_s = '1' then
                    sprite_info_taken_s <= '1';
                else
                    if update_sprite_info_s = '1' then
                        sprite_info_taken_s <= '0';
                    end if;
                end if; -- Sprite info taken

                if update_sprite_info_s = '1' then
                    burst_todo_s <= '1';
                else
                    if done_bursting_s = '1' then
                        burst_todo_s <= '0';
                    end if;
                end if; -- Burst Todo

                if effective_read_s = '1' then
                    burst_ready_s <= '0';
                else
                    if tileRom_burstDone = '1' then
                        burst_ready_s <= '1';
                    end if;
                end if; -- Burst Ready
            end if; -- Reset

            if update_sprite_info_s = '1' then
                raw_sprite_info_reg_s <= spriteRam_dout;
                sprite_info_reg_s <= sprite_info_s;
                burst_counter_max_s <= sprite_info_s.tile_size_x * sprite_info_s.tile_size_y;
            end if; -- Update Sprite Info Reg
        end if; -- Rising Edge Clock
    end process register_processs;

    -- This counter allows us to know which line of the sprite RAM to check to
    -- see if it has a sprite to be drawn.
    sprite_info_counter_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if reset_sprite_counters_s = '1' then
                sprite_info_counter_s <= (others => '0');
            else
                if next_sprite_info_s = '1' then
                    sprite_info_counter_s <= sprite_info_counter_s + 1;
                end if; -- Increment
            end if; -- Reset
        end if; -- Rising Edge Clock
    end process sprite_info_counter_process;

    -- These counters allow us to know when all the work has been done
    sprite_counters_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if reset_sprite_counters_s = '1' then
                sent_sprite_counter_s <= (others => '0');
                done_sprite_counter_s <= (others => '0');
            else
                if update_sprite_info_s = '1' then
                    sent_sprite_counter_s <= sent_sprite_counter_s + 1;
                end if; -- Increment

                if pipeline_done_blitting_s = '1' then
                    done_sprite_counter_s <= done_sprite_counter_s + 1;
                end if; -- Increment
            end if; -- Reset
        end if; -- Rising Edge Clock
    end process sprite_counters_process;

    -- When IDLE there is no work
    reset_sprite_counters_s <= '1' when state_reg_s = IDLE else '0';

    -- This counts how many tiles of the sprite have been bursted
    burst_counter_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if rst_i = '1' then
                burst_counter_s <= (others => '0');
            else
                if update_sprite_info_s = '1' then
                    burst_counter_s <= (others => '0');
                else
                    if effective_read_s = '1' then
                        burst_counter_s <= burst_counter_s + 1;
                    end if; -- Increment
                end if; -- Reset counter
            end if; -- Reset
        end if; -- Rising Edge Clock
    end process burst_counter_process;

    ----------
    -- FIFO --
    ----------
    tile_fifo_inst : entity work.tile_fifo
        port map (
            clock       => clk_i,
            data        => tileRom_dout,
            rdreq       => read_fifo_s,
            wrreq       => tileRom_valid,
            almost_full => fifo_prog_full_s,
            empty       => fifo_empty_s,
            q           => data_to_pipeline_s);

    --------------------
    -- Sprite Blitter --
    --------------------
    sprite_blitter_pipeline_inst : entity work.sprite_blitter_pipeline
        port map (
            clk_i                     => clk_i,
            rst_i                     => rst_i,
            sprite_info_i             => raw_sprite_info_reg_s,
            get_sprite_info_o         => pipeline_takes_sprite_info_s,
            sprite_burst_fifo_data_i  => data_to_pipeline_s,
            sprite_burst_fifo_read_o  => read_fifo_s,
            sprite_burst_fifo_empty_i => fifo_empty_s,
            paletteRam_rd             => paletteRam_rd,
            paletteRam_addr           => paletteRam_addr,
            paletteRam_dout           => paletteRam_dout,
            priority_read_rd          => priority_read_rd,
            priority_read_addr        => priority_read_addr,
            priority_read_dout        => priority_read_dout,
            priority_write_addr       => priority_write_addr,
            priority_write_din        => priority_write_din,
            priority_write_wr         => priority_write_wr,
            frameBuffer_wr            => frameBuffer_wr,
            frameBuffer_addr          => frameBuffer_addr,
            frameBuffer_mask          => frameBuffer_mask,
            frameBuffer_din           => frameBuffer_din,
            done_blitting_sprite_o    => pipeline_done_blitting_s);


    ---------
    -- FSM --
    ---------
    fsm_state_reg_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if rst_i = '1' then
                state_reg_s <= IDLE;
            else
                state_reg_s <= next_state_s;
            end if;
        end if;
    end process fsm_state_reg_process;

    fsm_next_state_process : process(all) is
    begin
        -- Default value, don't change
        next_state_s <= state_reg_s;

        case state_reg_s is

            when IDLE =>
                if start_i = '1' then
                    next_state_s <= BEGIN_CHECK;
                end if;

            when BEGIN_CHECK =>
                next_state_s <= WORKING;

            when WORKING =>
                if work_done_s = '1' then
                    next_state_s <= IDLE;
                end if;

        end case;
    end process fsm_next_state_process;

    -------------
    -- Outputs --
    -------------
    tileRom_rd <= sprite_burst_read_s;
    done_o <= work_done_s;
    -- Address by line (1024 lines) and buffer_select says which group of
    -- lines, the first or the second group.
    spriteRam_addr <= resize(sprite_bank_i & sprite_info_counter_s(9 downto 0), spriteRam_addr'length);
    -- The sprite rom address is the (code + burst counter) * byte_per_tile
    -- since the code indicates the first tile and the burst counter bursts by
    -- the same amount of bytes than a 16x16 tile.
    tileRom_addr <= resize((sprite_info_reg_s.code+burst_counter_s)*DDP_BYTES_PER_16x16_TILE, tileRom_addr'length);

    tileRom_burstLength <= x"10";
    spriteRam_rd <= '1';
end struct;
