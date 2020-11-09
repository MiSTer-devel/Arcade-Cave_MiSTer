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
-- File         : cave_top.vhd
-- Description  : This is a top for Dodonpachi, it is a layer to connect
--                Dodonpachi to the outside world, e.g. inputs and outputs as
--                well as the memories that are too big to be BRAM e.g., all
--                the ROMs, which amount to around 20MB are stored outside of
--                the FPGA and this layer connects them. This layer also does
--                some clock domain crossing since Dodonpachi's main processor
--                should run at around 16MHz and the rest of FPGA runs at
--                100MHz (e.g. HP interface to DDR3).
--                I say the main processor runs around 16MHz (which is the
--                value in real life) but since the VHDL core may not be cycle
--                accurate, this clock my need to be adjusted a bit to have a
--                similar speed as the real game.
--
-- Author       : Rick Wertenbroek
-- Version      : 0.1
--
-- Dependencies : All of its contents
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use work.cave_pkg.all;

entity cave_top is
    generic (
        SIMULATION_G                : boolean := false;
        INCLUDE_GRAPHIC_PROCESSOR_G : boolean := true
        );
    port (
        -- Clock and reset
        rst_i                    : in  std_logic;
        rst_68k_i                : in  std_logic;
        clk_i                    : in  std_logic;
        clk_68k_i                : in  std_logic;
        -- Player input signals
        player_1_i               : in  std_logic_vector(8 downto 0);
        player_2_i               : in  std_logic_vector(8 downto 0);
        pause_i                  : in  std_logic;
        -- Memory interface for the ROM
        rom_addr_68k_cache_o     : out unsigned(DDP_ROM_LOG_SIZE_C-1 downto 0);
        rom_read_68k_cache_o     : out std_logic;
        rom_valid_68k_cache_i    : in  std_logic;
        rom_data_68k_cache_i     : in  std_logic_vector(DDP_ROM_CACHE_LINE_WIDTH-1 downto 0);
        -- GFX
        rom_addr_gfx_o           : out gfx_rom_addr_t;
        tiny_burst_gfx_o         : out std_logic;
        rom_burst_read_gfx_o     : out std_logic;
        rom_data_valid_gfx_i     : in  std_logic;
        rom_data_gfx_i           : in  gfx_rom_data_t;
        rom_burst_done_gfx_i     : in  std_logic;
        -- Frame Buffer
        frame_buffer_addr_o      : out frame_buffer_addr_t;
        frame_buffer_data_o      : out std_logic_vector(DDP_WORD_WIDTH-2 downto 0);
        frame_buffer_write_o     : out std_logic;
        frame_buffer_dma_start_o : out std_logic;
        frame_buffer_dma_done_i  : in  std_logic;
        -- Vertical blank signal
        vblank_i                 : in std_logic;
        -- Debug
        TG68_PC_o                : out std_logic_vector(31 downto 0);
        TG68_PCW_o               : out std_logic
        );
end entity cave_top;

architecture struct of cave_top is

    -------------
    -- Signals --
    -------------

    -- 68k related signals
    signal rom_addr_from_68k_s    : unsigned(DDP_ROM_LOG_SIZE_C-1 downto 0);
    signal rom_read_from_68k_s    : std_logic;
    signal rom_data_to_68k_s      : word_t;
    signal rom_valid_to_68k_s     : std_logic;

    -- Cache memory related signals
    signal rom_addr_from_cache_s  : unsigned(DDP_ROM_LOG_SIZE_C-1 downto 0);
    signal rom_read_from_cache_s  : std_logic;
    signal rom_valid_to_cache_s   : std_logic;
    signal rom_data_to_cache_s    : std_logic_vector(DDP_ROM_CACHE_LINE_WIDTH-1 downto 0);

    -- Frame buffer conversion
    signal frame_buffer_color_s   : color_t;

begin

    --------------
    -- Main PCB --
    --------------
    cave : entity work.cave
        generic map (
            INCLUDE_GRAPHIC_PROCESSOR_G => INCLUDE_GRAPHIC_PROCESSOR_G)
        port map (
            rst_i                    => rst_i,
            rst_68k_i                => rst_68k_i,
            clk_fast_i               => clk_i,
            clk_68k_i                => clk_68k_i,
            player_1_i               => player_1_i,
            player_2_i               => player_2_i,
            pause_i                  => pause_i,
            rom_addr_68k_o           => rom_addr_from_68k_s,
            rom_read_68k_o           => rom_read_from_68k_s,
            rom_valid_68k_i          => rom_valid_to_68k_s,
            rom_data_68k_i           => rom_data_to_68k_s,
            rom_addr_gfx_o           => rom_addr_gfx_o,
            tiny_burst_gfx_o         => tiny_burst_gfx_o,
            rom_burst_read_gfx_o     => rom_burst_read_gfx_o,
            rom_data_valid_gfx_i     => rom_data_valid_gfx_i,
            rom_data_gfx_i           => rom_data_gfx_i,
            rom_burst_done_gfx_i     => rom_burst_done_gfx_i,
            frame_buffer_addr_o      => frame_buffer_addr_o,
            frame_buffer_color_o     => frame_buffer_color_s,
            frame_buffer_write_o     => frame_buffer_write_o,
            frame_buffer_dma_start_o => frame_buffer_dma_start_o,
            frame_buffer_dma_done_i  => frame_buffer_dma_done_i,
            vblank_i                 => vblank_i,
            TG68_PC_o                => TG68_PC_o,
            TG68_PCW_o               => TG68_PCW_o);

    frame_buffer_data_o <= frame_buffer_color_s.r & frame_buffer_color_s.g & frame_buffer_color_s.b;
    -- TODO : FIX EVERYWHERE - CAVE 1st gen stores as GRB555 not RGB555
    --frame_buffer_data_o <= frame_buffer_color_s.g & frame_buffer_color_s.r & frame_buffer_color_s.b;

    ------------------
    -- Cache Memory --
    ------------------

    -- Cache memory to reduce the number of accesses to the external memory.
    cache_memory : entity work.cache_memory
        generic map (
            ADDRESS_BITS_G     => DDP_ROM_LOG_SIZE_C,
            --LOG_CACHE_LINES_G  => LOG_CACHE_LINES_G,
            LOG_CACHE_LINES_G  => 8,
            WORD_BYTE_SIZE_G   => DDP_WORD_WIDTH/8,
            CACHE_LINE_WORDS_G => DDP_ROM_CACHE_LINE_WORDS,
            PERF_COUNT_EN_G    => true)
            --PERF_COUNT_EN_G    => SIMULATION_G) -- This is for performance monitoring
        port map (
            clk_i                   => clk_68k_i,
            rst_i                   => rst_68k_i,
            agent_to_cache_addr_i   => rom_addr_from_68k_s,
            agent_to_cache_read_i   => rom_read_from_68k_s,
            cache_to_agent_data_o   => rom_data_to_68k_s,
            cache_to_agent_valid_o  => rom_valid_to_68k_s,
            cache_to_memory_addr_o  => rom_addr_from_cache_s,
            cache_to_memory_read_o  => rom_read_from_cache_s,
            memory_to_cache_data_i  => rom_data_to_cache_s,
            memory_to_cache_valid_i => rom_valid_to_cache_s,
            req_counter_o           => open,
            miss_counter_o          => open);

    ---------------------------
    -- Clock Domain Crossing --
    ---------------------------
    kel_thuzad : entity work.data_freezer
        port map (
            slow_clk_i          => clk_68k_i,
            slow_rst_i          => rst_68k_i,
            fast_clk_i          => clk_i,
            fast_rst_i          => rst_i,
            data_sc_i           => std_logic_vector(rom_addr_from_cache_s),
            write_sc_i          => rom_read_from_cache_s,
            data_sc_o           => rom_data_to_cache_s,
            valid_sc_o          => rom_valid_to_cache_s,
            data_fc_i           => rom_data_68k_cache_i,
            write_fc_i          => rom_valid_68k_cache_i,
            unsigned(data_fc_o) => rom_addr_68k_cache_o,
            valid_fc_o          => rom_read_68k_cache_o);

end struct;
