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
-- File         : vhdl_top.vhd
-- Description  :
--
-- Author       : Rick Wertenbroek
-- Version      : 0.1
--
-- Dependencies : hdmi_pkg.vhd, bram, clk_mmcm, debug_display.vhd,
--                hdmi_out.vhd, cave_pkg.vhd
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use work.cave_pkg.all;

entity vhdl_top is
    generic (
        USE_FAKE_FRAME_BUFFER       : boolean := false;
        INCLUDE_GRAPHIC_PROCESSOR_G : boolean := true
    );
    port (
        -- Fast clock domain
        rst_i                     : in  std_logic;
        clk_i                     : in  std_logic;
        -- Video clock domain
        clk_video_i               : in  std_logic;
        -- CPU clock domain
        rst_68k_i                 : in  std_logic;
        clk_68k_i                 : in  std_logic;
        -- Player input signals
        player_1_i                : in  std_logic_vector(8 downto 0);
        player_2_i                : in  std_logic_vector(8 downto 0);
        pause_i                   : in  std_logic;
        -- Control signals
        base_addr_prog_rom_i      : in  unsigned(31 downto 0);
        base_addr_sprite_rom_i    : in  unsigned(31 downto 0);
        base_addr_layer_rom_i     : in  unsigned(31 downto 0);
        -- Memory out interface
        rom_addr_68k_cache_o      : out unsigned(31 downto 0);
        rom_read_68k_cache_o      : out std_logic;
        rom_valid_68k_cache_i     : in  std_logic;
        rom_data_68k_cache_i      : in  std_logic_vector(DDP_ROM_CACHE_LINE_WIDTH-1 downto 0);
        -- Graphics
        rom_addr_gfx_o            : out gfx_rom_addr_t;
        tiny_burst_gfx_o          : out std_logic;
        rom_burst_read_gfx_o      : out std_logic;
        rom_data_gfx_i            : in  gfx_rom_data_t;
        rom_data_valid_gfx_i      : in  std_logic;
        rom_burst_done_gfx_i      : in  std_logic;
        -- Frame buffer
        frame_buffer_dma_start_o  : out std_logic;
        frame_buffer_dma_done_i   : in  std_logic;
        frame_buffer_data_o       : out std_logic_vector(63 downto 0); -- For Avalon
        frame_buffer_addr_i       : in  unsigned(DDP_FRAME_BUFFER_ADDR_BITS-3 downto 0);
        frame_buffer_swap_o       : out std_logic;
        -- Video signals
        video_x_o                 : out unsigned(8 downto 0);
        video_y_o                 : out unsigned(8 downto 0);
        video_hsync_o             : out std_logic;
        video_vsync_o             : out std_logic;
        video_hblank_o            : out std_logic;
        video_vblank_o            : out std_logic;
        video_enable_o            : out std_logic;
        -- Debug
        tg68_addr_o               : out std_logic_vector(9 downto 0);
        tg68_pc_o                 : out std_logic_vector(31 downto 0);
        tg68_pcw_o                : out std_logic
        );
end entity vhdl_top;

architecture struct of vhdl_top is
    signal video_s : video_t;

    signal frame_buffer_addr_s  : frame_buffer_addr_t;
    signal frame_buffer_data_s  : std_logic_vector(DDP_WORD_WIDTH-2 downto 0);
    signal frame_buffer_write_s : std_logic;
begin

    --------------
    -- Game top --
    --------------
    cave_top_block : block
        signal rom_addr_68k_cache_s  : unsigned(19 downto 0); -- TODO constant
        signal rom_addr_gfx_s        : unsigned(31 downto 0); -- TODO constant
        signal tg68_pc_s             : std_logic_vector(31 downto 0); -- TODO constant
        signal tg68_pcw_s            : std_logic;
        signal pc_counter_s          : unsigned(tg68_addr_o'length downto 0);
    begin

        cave_top_inst : entity work.cave_top
            generic map (
                INCLUDE_GRAPHIC_PROCESSOR_G => INCLUDE_GRAPHIC_PROCESSOR_G)
            port map (
                rst_i                    => rst_i,
                clk_i                    => clk_i,
                --
                rst_68k_i                => rst_68k_i,
                clk_68k_i                => clk_68k_i,
                --
                player_1_i               => player_1_i,
                player_2_i               => player_2_i,
                pause_i                  => pause_i,
                --
                rom_addr_68k_cache_o     => rom_addr_68k_cache_s,
                rom_read_68k_cache_o     => rom_read_68k_cache_o,
                rom_valid_68k_cache_i    => rom_valid_68k_cache_i,
                rom_data_68k_cache_i     => rom_data_68k_cache_i,
                --
                rom_addr_gfx_o           => rom_addr_gfx_s,
                tiny_burst_gfx_o         => tiny_burst_gfx_o,
                rom_burst_read_gfx_o     => rom_burst_read_gfx_o,
                rom_data_valid_gfx_i     => rom_data_valid_gfx_i,
                rom_data_gfx_i           => rom_data_gfx_i,
                rom_burst_done_gfx_i     => rom_burst_done_gfx_i,
                --
                frame_buffer_addr_o      => frame_buffer_addr_s,
                frame_buffer_data_o      => frame_buffer_data_s,
                frame_buffer_write_o     => frame_buffer_write_s,
                frame_buffer_dma_start_o => frame_buffer_dma_start_o,
                frame_buffer_dma_done_i  => frame_buffer_dma_done_i,
                --
                vblank_i                 => video_s.vblank,
                --
                TG68_PC_o                => tg68_pc_s,
                TG68_PCW_o               => tg68_pcw_s);

        -- Address conversion
        rom_addr_68k_cache_o <= base_addr_prog_rom_i + rom_addr_68k_cache_s;
        rom_addr_gfx_o       <= base_addr_sprite_rom_i + rom_addr_gfx_s;

        -- This counter gives the execution trace address
        process (clk_68k_i) is
        begin
            if rising_edge(clk_68k_i) then
                if rst_68k_i = '1' then
                    pc_counter_s <= (others => '0');
                elsif (tg68_pcw_s = '1') then
                    pc_counter_s <= pc_counter_s + 1;
                end if;
            end if;
        end process;

        tg68_addr_o <= std_logic_vector(pc_counter_s(tg68_addr_o'length-1 downto 0));
        -- Use MSB bit to show which are the newest instructions
        tg68_pc_o   <= pc_counter_s(pc_counter_s'high) & tg68_pc_s(30 downto 0);
        tg68_pcw_o  <= tg68_pcw_s;

    end block cave_top_block;

    ------------------
    -- Video Timing --
    ------------------
    video_timing_block : block
    begin
        -- horizontal frequency: 6Mhz / 382 = 15.707kHz
        -- vertical frequency: 15.707kHz / 273 = 57.73 Hz
        video_timing_inst : entity work.video_timing
        generic map (
            H_DISPLAY     => 320,
            H_FRONT_PORCH => 5,
            H_RETRACE     => 23,
            H_BACK_PORCH  => 34,
            V_DISPLAY     => 240,
            V_FRONT_PORCH => 12,
            V_RETRACE     => 2,
            V_BACK_PORCH  => 19
        )
        port map (
            clk   => clk_video_i,
            video => video_s
        );

        -- Video signals
        video_x_o      <= video_s.pos.x;
        video_y_o      <= video_s.pos.y;
        video_hsync_o  <= video_s.hsync;
        video_vsync_o  <= video_s.vsync;
        video_hblank_o <= video_s.hblank;
        video_vblank_o <= video_s.vblank;
        video_enable_o <= video_s.enable;
    end block video_timing_block;

    ------------------
    -- Frame Buffer --
    ------------------
    frame_buffer_block : block
        signal doutb_s                              : std_logic_vector(59 downto 0);
        signal frame_buffer_addr_new_geometry_reg_s : unsigned(frame_buffer_addr_s'length-1 downto 0);
        signal frame_buffer_data_reg_s              : std_logic_vector(frame_buffer_data_s'length-1 downto 0);
        signal frame_buffer_write_reg_s             : std_logic;

        signal x_s           : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_X-1 downto 0);
        signal reverse_x_s   : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_X-1 downto 0);
        signal y_s           : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_Y-1 downto 0);
        signal x_mult_s      : unsigned(16 downto 0);
        signal y_mult_s      : unsigned(16 downto 0);
        signal h_address_s   : unsigned(frame_buffer_addr_s'length-1 downto 0);
        signal v_address_s   : unsigned(frame_buffer_addr_s'length-1 downto 0);
    begin
        -- Convert the X & Y address to a linear one
        x_s           <= frame_buffer_addr_s(frame_buffer_addr_s'high downto frame_buffer_addr_s'length-DDP_FRAME_BUFFER_ADDR_BITS_X);
        y_s           <= frame_buffer_addr_s(DDP_FRAME_BUFFER_ADDR_BITS_Y-1 downto 0);
        reverse_x_s   <= to_unsigned(DDP_VISIBLE_SCREEN_WIDTH-1, DDP_FRAME_BUFFER_ADDR_BITS_X)-x_s;
        x_mult_s      <= reverse_x_s*to_unsigned(DDP_VISIBLE_SCREEN_HEIGHT, DDP_FRAME_BUFFER_ADDR_BITS_Y);
        y_mult_s      <= y_s*to_unsigned(DDP_VISIBLE_SCREEN_WIDTH, DDP_FRAME_BUFFER_ADDR_BITS_X);
        h_address_s   <= y_mult_s+x_s;
        v_address_s   <= x_mult_s+y_s;

        -- Write requests are registered in order to have better timings
        process(clk_i) is
        begin
            if rising_edge(clk_i) then
                if rst_i = '1' then
                    frame_buffer_write_reg_s <= '0';
                else
                    frame_buffer_write_reg_s <= frame_buffer_write_s;
                end if;

                frame_buffer_data_reg_s <= frame_buffer_data_s;

                -- Horizontal (needs screen rotation)
                frame_buffer_addr_new_geometry_reg_s <= h_address_s;
                -- Vertical
                --frame_buffer_addr_new_geometry_reg_s <= std_logic_vector(v_address_s);
            end if;
        end process;

        frame_buffer_generate : if USE_FAKE_FRAME_BUFFER generate
            -- This is a fake frame buffer that ignores all writes and simply
            -- returns the address one cycle later upon read.
            fake_frame_buffer_inst : entity work.fake_frame_buffer
                port map (
                    clka   => clk_i,
                    wea(0) => frame_buffer_write_reg_s,
                    addra  => frame_buffer_addr_new_geometry_reg_s,
                    dina   => frame_buffer_data_reg_s,
                    clkb   => clk_i,
                    addrb  => frame_buffer_addr_i,
                    doutb  => doutb_s);
        else generate
            -- This is a temporary frame buffer that allows fast (single cycle) random
            -- access to any position. This is used so that the graphical pipeline can
            -- run at full speed. Once the image is generated it is transfered by DMA
            -- to the dual frame buffer in RAM. This is all done faster than
            -- the game refresh rate so there is no lag here, it's just that it
            -- is faster to do the random accesses to a BRAM and when finished
            -- burst it to the DDR3 instead of directly writing small words to
            -- the DDR3. Again, this induces no lag.
            frame_buffer_inst : entity work.dual_port_ram
                generic map (
                    ADDR_WIDTH_A => DDP_FRAME_BUFFER_ADDR_BITS,
                    DATA_WIDTH_A => DDP_FRAME_BUFFER_COLOR_BITS,
                    DEPTH_A      => DDP_VISIBLE_SCREEN_WIDTH*DDP_VISIBLE_SCREEN_HEIGHT,
                    ADDR_WIDTH_B => DDP_FRAME_BUFFER_ADDR_BITS-2,
                    DATA_WIDTH_B => DDP_FRAME_BUFFER_COLOR_BITS*4,
                    DEPTH_B      => DDP_VISIBLE_SCREEN_WIDTH*DDP_VISIBLE_SCREEN_HEIGHT/4)
                port map (
                    clk    => clk_i,
                    we_a   => frame_buffer_write_reg_s,
                    addr_a => frame_buffer_addr_new_geometry_reg_s,
                    din_a  => frame_buffer_data_reg_s,
                    addr_b => frame_buffer_addr_i,
                    dout_b => doutb_s);
        end generate;

        -- Put the pixels four by four. Endianness can be changed here if needed
        frame_buffer_data_o <= "0" & doutb_s(59 downto 45) & "0" & doutb_s(44 downto 30) & "0" & doutb_s(29 downto 15) & "0" & doutb_s(14 downto 0);

    end block frame_buffer_block;

    interrupt_block : block
        signal sync_reg_s : std_logic_vector(3 downto 0);
        signal frame_buffer_swap_reg_s : std_logic;
    begin
        -- Synchronise the VBLANK signal into the fast clock domain
        sync_process : process(clk_i) is
        begin
            if rising_edge(clk_i) then
                sync_reg_s(0) <= video_s.vblank;
                for i in 1 to sync_reg_s'high loop
                    sync_reg_s(i) <= sync_reg_s(i-1);
                end loop;
            end if;
        end process sync_process;

        -- Toggle the swap register on the rising edge of the VBLANK signal
        frame_buffer_swap : process(clk_i) is
        begin
            if rising_edge(clk_i) then
                if (not sync_reg_s(sync_reg_s'high)) and sync_reg_s(sync_reg_s'high-1) then
                    frame_buffer_swap_reg_s <= not frame_buffer_swap_reg_s;
                end if;
            end if;
        end process frame_buffer_swap;

        frame_buffer_swap_o <= frame_buffer_swap_reg_s;
    end block interrupt_block;
end architecture struct;
