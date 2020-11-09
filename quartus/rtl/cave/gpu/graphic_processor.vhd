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
-- File         : graphic_processor.vhd
-- Description  : This is the graphic processor of Dodonpachi. It holds a
--                sprite processor and three layer processors. On the real PCB
--                these 4 processors are U23, UA3, UB2, and UC1. This
--                implementation only uses a single frame buffer and not a dual
--                one like on the PCB. This is because this implementation is
--                fast enough to process both images one after the other and
--                dual buffering would require double the memory.
--
-- Author       : Rick Wertenbroek
-- Version      : 0.0
--
-- VHDL std     : 2008
-- Dependencies : cave_pkg.vhd, sprite_processor.vhd
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use work.cave_pkg.all;

entity graphic_processor is
    generic (
        INCLUDE_LAYER_PROCESOR_G : boolean := false);
    port (
        -- Standard signals
        clk_i                    : in  std_logic;
        rst_i                    : in  std_logic;
        -- Control signals
        generate_frame_i         : in  std_logic;
        buffer_select_i          : in  std_logic;
        layer_processor_en_i     : in  std_logic := '1';
        -- Sprite RAM interface
        sprite_ram_addr_o        : out sprite_ram_info_access_t;
        sprite_ram_info_i        : in  sprite_ram_line_t := (others => '0');
        -- Layer 0 RAM interface
        layer_0_ram_addr_o       : out layer_ram_info_access_t;
        layer_0_ram_info_i       : in  layer_ram_line_t := (others => '0');
        -- Layer 1 RAM interface
        layer_1_ram_addr_o       : out layer_ram_info_access_t;
        layer_1_ram_info_i       : in  layer_ram_line_t := (others => '0');
        -- Layer 2 RAM interface
        layer_2_ram_addr_o       : out layer_ram_info_access_t;
        layer_2_ram_info_i       : in  layer_ram_line_t := (others => '0');
        -- Video Control Regs
        vctrl_reg_0_i            : in  layer_info_line_t := (others => '0');
        vctrl_reg_1_i            : in  layer_info_line_t := (others => '0');
        vctrl_reg_2_i            : in  layer_info_line_t := (others => '0');
        -- ROM interface (both for sprite ROM and layer ROM)
        rom_addr_o               : out gfx_rom_addr_t;
        tiny_burst_gfx_o         : out std_logic;
        rom_burst_read_o         : out std_logic;
        rom_data_i               : in  gfx_rom_data_t;
        rom_data_valid_i         : in  std_logic;
        rom_data_burst_done_i    : in  std_logic;
        -- Palette RAM interface (Do not delay ! It expects data the next cycle)
        palette_ram_addr_o       : out palette_ram_addr_t;
        palette_ram_data_i       : in  palette_ram_data_t;
        -- Frame Buffer interface
        frame_buffer_addr_o      : out frame_buffer_addr_t;
        frame_buffer_color_o     : out color_t;
        frame_buffer_write_o     : out std_logic;
        frame_buffer_dma_start_o : out std_logic;
        frame_buffer_dma_done_i  : in  std_logic);
end entity graphic_processor;

architecture struct of graphic_processor is

    -----------
    -- Types --
    -----------

    -- NOTE : Maybe an extra stage is needed to clear the frame buffer (make it
    -- all black), this is not needed if the layers span the entire screen.
    type state_t is (IDLE, CLEAR_FB, DRAW_SPRITES, DRAW_LAYER_0, DRAW_LAYER_1, DRAW_LAYER_2, START_DMA, WAIT_DMA);

    -------------
    -- Signals --
    -------------
    signal state_reg_s, next_state_s       : state_t;
    --
    signal clear_fb_done_s                 : std_logic;
    --
    signal start_drawing_sprites_s         : std_logic;
    signal start_drawing_layer_s           : std_logic;
    signal sprite_processor_done_s         : std_logic;
    signal layer_processor_done_s          : std_logic;
    -- --
    signal spr_rom_addr_s                  : gfx_rom_addr_t;
    signal spr_burst_read_s                : std_logic;
    signal spr_data_s                      : gfx_rom_data_t;
    signal spr_data_valid_s                : std_logic;
    signal spr_data_burst_done_s           : std_logic;
    --
    signal spr_priority_ram_read_addr_s    : priority_ram_addr_t;
    signal spr_priority_ram_read_data_s    : priority_t;
    signal spr_priority_ram_write_addr_s   : priority_ram_addr_t;
    signal spr_priority_ram_write_data_s   : priority_t;
    signal spr_priority_ram_write_s        : std_logic;
    --
    signal spr_palette_ram_addr_s          : palette_ram_addr_t;
    --
    signal spr_frame_buffer_addr_s         : frame_buffer_addr_t;
    signal spr_frame_buffer_color_s        : color_t;
    signal spr_frame_buffer_write_s        : std_logic;
    -- --
    signal layer_rom_addr_s                : gfx_rom_addr_t;
    signal tiny_burst_s                    : std_logic;
    signal layer_burst_read_s              : std_logic;
    signal layer_data_s                    : gfx_rom_data_t;
    signal layer_data_valid_s              : std_logic;
    signal layer_data_burst_done_s         : std_logic;
    --
    signal layer_priority_ram_read_addr_s  : priority_ram_addr_t;
    signal layer_priority_ram_read_data_s  : priority_t;
    signal layer_priority_ram_write_addr_s : priority_ram_addr_t;
    signal layer_priority_ram_write_data_s : priority_t;
    signal layer_priority_ram_write_s      : std_logic;
    --
    signal layer_palette_ram_addr_s        : palette_ram_addr_t;
    --
    signal layer_frame_buffer_addr_s       : frame_buffer_addr_t;
    signal layer_frame_buffer_color_s      : color_t;
    signal layer_frame_buffer_write_s      : std_logic;
    --
    signal layer_number_s                  : unsigned(1 downto 0);
    signal layer_info_to_layer_processor_s : layer_info_t;
    -- --
    signal clear_frame_buffer_addr_s       : frame_buffer_addr_t;
    signal clear_frame_buffer_color_s      : color_t;
    signal clear_frame_buffer_write_s      : std_logic;
    signal clear_priority_ram_addr_s       : priority_ram_addr_t;
    signal clear_priority_ram_write_s      : std_logic;
    signal clear_priority_ram_data_s       : priority_t;

begin

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
            end if; -- Reset
        end if; -- Rising Edge Clock
    end process fsm_state_reg_process;

    fsm_next_state_process : process(all) is
    begin
        -- Default behavior, stay in the same state
        next_state_s <= state_reg_s;

        case state_reg_s is
            when IDLE =>
                if generate_frame_i = '1' then
                    next_state_s <= CLEAR_FB;
                end if;

            when CLEAR_FB =>
                if clear_fb_done_s = '1' then
                    next_state_s <= DRAW_SPRITES;
                end if;

            when DRAW_SPRITES =>
                if sprite_processor_done_s = '1' then
                    if layer_processor_en_i = '1' then
                        next_state_s <= DRAW_LAYER_0;
                    else
                        next_state_s <= START_DMA;
                    end if;
                end if;

            when DRAW_LAYER_0 =>
                if layer_processor_done_s = '1' then
                    next_state_s <= DRAW_LAYER_1;
                end if;

            when DRAW_LAYER_1 =>
                if layer_processor_done_s = '1' then
                    next_state_s <= DRAW_LAYER_2;
                end if;

            when DRAW_LAYER_2 =>
                if layer_processor_done_s = '1' then
                    next_state_s <= START_DMA;
                end if;

            when START_DMA =>
                next_state_s <= WAIT_DMA;

            when WAIT_DMA =>
                if frame_buffer_dma_done_i = '1' then
                    next_state_s <= IDLE;
                end if;
        end case;

    end process fsm_next_state_process;

    -------------------
    -- Control Logic --
    -------------------
    start_drawing_sprites_s <= '1' when next_state_s = DRAW_SPRITES else '0';

    process(clk_i)
    begin
        if rising_edge(clk_i) then
            -- Upon entry in a DRAW_LAYER state set the start drawing layer signal
            if ((next_state_s = DRAW_LAYER_0) and (state_reg_s /= DRAW_LAYER_0)) or
               ((next_state_s = DRAW_LAYER_1) and (state_reg_s /= DRAW_LAYER_1)) or
               ((next_state_s = DRAW_LAYER_2) and (state_reg_s /= DRAW_LAYER_2)) then
                start_drawing_layer_s <= '1';
            -- Deassert the signal (pulse)
            else
                start_drawing_layer_s <= '0';
            end if;
        end if;
    end process;

    layer_number_s <= "00" when state_reg_s = DRAW_LAYER_0 else
                      "01" when state_reg_s = DRAW_LAYER_1 else
                      "10" when state_reg_s = DRAW_LAYER_2 else
                      "00";

    frame_buffer_dma_start_o <= '1' when state_reg_s = START_DMA else '0';

    ----------------
    -- Processors --
    ----------------

    -- Clear processor
    clear_processor_block : block
        signal counter_x : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_X-1 downto 0);
        signal counter_y : unsigned(DDP_FRAME_BUFFER_ADDR_BITS_Y-1 downto 0);
    begin
        -- Will write zero to all pixels in the frame buffer
        counter_process : process(clk_i) is
        begin
            if rising_edge(clk_i) then
                if state_reg_s /= CLEAR_FB then
                    counter_x <= (others => '0');
                    counter_y <= (others => '0');
                else
                    if counter_x = (DDP_VISIBLE_SCREEN_WIDTH-1) then
                        counter_x <= (others => '0');
                        counter_y <= counter_y + 1;
                    else
                        counter_x <= counter_x + 1;
                    end if;
                end if;
            end if;
        end process counter_process;

        clear_fb_done_s <= '1' when (counter_x = DDP_VISIBLE_SCREEN_WIDTH-1) and (counter_y = DDP_VISIBLE_SCREEN_HEIGHT-1) else '0';

        clear_frame_buffer_addr_s  <= counter_x & counter_y;
        clear_frame_buffer_write_s <= '1'; -- always write
        clear_frame_buffer_color_s <= (others => (others => '0')); -- black

        clear_priority_ram_addr_s  <= counter_x & counter_y;
        clear_priority_ram_write_s <= '1'; -- always write
        clear_priority_ram_data_s  <= (others => '0'); -- Lowest

    end block clear_processor_block;

    -- Sprite Processor
    sprite_processor_inst : entity work.sprite_processor
        port map (
            clk_i                     => clk_i,
            rst_i                     => rst_i,
            --
            start_i                   => start_drawing_sprites_s,
            buffer_select_i           => buffer_select_i,
            done_o                    => sprite_processor_done_s,
            --
            sprite_ram_addr_o         => sprite_ram_addr_o,
            sprite_ram_info_i         => sprite_ram_info_i,
            --
            sprite_rom_addr_o         => spr_rom_addr_s,
            sprite_burst_read_o       => spr_burst_read_s,
            sprite_data_i             => spr_data_s,
            sprite_data_valid_i       => spr_data_valid_s,
            sprite_data_burst_done_i  => spr_data_burst_done_s,
            --
            priority_ram_read_addr_o  => spr_priority_ram_read_addr_s,
            priority_ram_data_i       => spr_priority_ram_read_data_s,
            priority_ram_write_addr_o => spr_priority_ram_write_addr_s,
            priority_ram_data_o       => spr_priority_ram_write_data_s,
            priority_ram_write_o      => spr_priority_ram_write_s,
            --
            palette_ram_addr_o        => spr_palette_ram_addr_s,
            palette_ram_data_i        => palette_ram_data_i,
            --
            frame_buffer_addr_o       => spr_frame_buffer_addr_s,
            frame_buffer_color_o      => spr_frame_buffer_color_s,
            frame_buffer_write_o      => spr_frame_buffer_write_s);

    -- Layer processor (will be the same but used with 3 different RAMs/ROMs)
    layer_processor_generate : if INCLUDE_LAYER_PROCESOR_G generate

        layer_processor_block : block
            -- The layer ROMs will be appended after the sprite ROMs
            constant layer_0_rom_offset_c : unsigned(31 downto 0) := x"00800000";
            constant layer_1_rom_offset_c : unsigned(31 downto 0) := x"00A00000";
            constant layer_2_rom_offset_c : unsigned(31 downto 0) := x"00C00000";

            signal layer_ram_addr_s : layer_ram_info_access_t;
            signal layer_ram_info_s : layer_ram_line_t;

            signal layer_processor_rom_offset_s : unsigned(31 downto 0);
        begin

            layer_processor_inst : entity work.layer_processor
                port map (
                    clk_i                     => clk_i,
                    rst_i                     => rst_i,
                    start_i                   => start_drawing_layer_s,
                    done_o                    => layer_processor_done_s,
                    layer_number_i            => layer_number_s,
                    layer_info_i              => layer_info_to_layer_processor_s,
                    layer_ram_addr_o          => layer_ram_addr_s,
                    layer_ram_info_i          => layer_ram_info_s,
                    layer_rom_addr_o          => layer_processor_rom_offset_s,
                    tiny_burst_o              => tiny_burst_s,
                    layer_burst_read_o        => layer_burst_read_s,
                    layer_data_i              => layer_data_s,
                    layer_data_valid_i        => layer_data_valid_s,
                    layer_data_burst_done_i   => layer_data_burst_done_s,
                    priority_ram_read_addr_o  => layer_priority_ram_read_addr_s,
                    priority_ram_data_i       => layer_priority_ram_read_data_s,
                    priority_ram_write_addr_o => layer_priority_ram_write_addr_s,
                    priority_ram_data_o       => layer_priority_ram_write_data_s,
                    priority_ram_write_o      => layer_priority_ram_write_s,
                    palette_ram_addr_o        => layer_palette_ram_addr_s,
                    palette_ram_data_i        => palette_ram_data_i,
                    frame_buffer_addr_o       => layer_frame_buffer_addr_s,
                    frame_buffer_color_o      => layer_frame_buffer_color_s,
                    frame_buffer_write_o      => layer_frame_buffer_write_s);

            -- Global Layer Info Mux
            layer_info_to_layer_processor_s <= extract_global_layer_info_from_regs(vctrl_reg_0_i) when state_reg_s = DRAW_LAYER_0 else
                                               extract_global_layer_info_from_regs(vctrl_reg_1_i) when state_reg_s = DRAW_LAYER_1 else
                                               extract_global_layer_info_from_regs(vctrl_reg_2_i);

            -- All get the same address, only one is used at a time
            layer_0_ram_addr_o <= layer_ram_addr_s;
            layer_1_ram_addr_o <= layer_ram_addr_s;
            layer_2_ram_addr_o <= layer_ram_addr_s;

            -- Layer RAM Info Mux
            layer_ram_info_s <= layer_0_ram_info_i when state_reg_s = DRAW_LAYER_0 else
                                layer_1_ram_info_i when state_reg_s = DRAW_LAYER_1 else
                                layer_2_ram_info_i;

            -- Get the correct layer rom address
            layer_rom_addr_s <= layer_processor_rom_offset_s + layer_0_rom_offset_c when state_reg_s = DRAW_LAYER_0 else
                                layer_processor_rom_offset_s + layer_1_rom_offset_c when state_reg_s = DRAW_LAYER_1 else
                                layer_processor_rom_offset_s + layer_2_rom_offset_c;

        end block layer_processor_block;

    else generate

        -- No layer processor
        layer_0_ram_addr_o              <= (others => '0');
        layer_1_ram_addr_o              <= (others => '0');
        layer_2_ram_addr_o              <= (others => '0');
        --
        tiny_burst_s                    <= '0';
        layer_rom_addr_s                <= (others => '0');
        layer_burst_read_s              <= '0';
        --
        layer_priority_ram_read_addr_s  <= (others => '0');
        layer_priority_ram_write_addr_s <= (others => '0');
        layer_priority_ram_write_data_s <= (others => '0');
        layer_priority_ram_write_s      <= '0';
        --
        layer_palette_ram_addr_s        <= (others => '0');
        --
        layer_frame_buffer_addr_s       <= (others => '0');
        layer_frame_buffer_color_s      <= (others => (others => '0'));
        layer_frame_buffer_write_s      <= '0';
        --
        layer_processor_done_s          <= '1';

    end generate;

    -------------------------
    -- Join the processors --
    -------------------------

    -- This section is to join the signals of the processors onto the shared
    -- busses, ROM, priority, palette, and frame buffer.
    -- Note : This can be optimized by only muxing the control signals and not
    -- the data signals, another possible optimization is to do an "OR'ed" bus

    -- ROM memory bus
    -----------------

    rom_addr_o               <= spr_rom_addr_s when state_reg_s = DRAW_SPRITES else
                                layer_rom_addr_s when (state_reg_s = DRAW_LAYER_0) or (state_reg_s = DRAW_LAYER_1) or (state_reg_s = DRAW_LAYER_2) else
                                (others => '0');
    tiny_burst_gfx_o         <= '0' when state_reg_s = DRAW_SPRITES else tiny_burst_s;
    rom_burst_read_o         <= spr_burst_read_s when state_reg_s = DRAW_SPRITES else layer_burst_read_s;
    spr_data_s               <= rom_data_i;
    layer_data_s             <= rom_data_i;
    spr_data_valid_s         <= rom_data_valid_i when state_reg_s = DRAW_SPRITES else '0';
    layer_data_valid_s       <= rom_data_valid_i when (state_reg_s = DRAW_LAYER_0) or (state_reg_s = DRAW_LAYER_1) or (state_reg_s = DRAW_LAYER_2) else '0';
    spr_data_burst_done_s    <= rom_data_burst_done_i when state_reg_s = DRAW_SPRITES else '0';
    layer_data_burst_done_s  <= rom_data_burst_done_i when (state_reg_s = DRAW_LAYER_0) or (state_reg_s = DRAW_LAYER_1) or (state_reg_s = DRAW_LAYER_2) else '0';

    -- Palette memory bus
    ---------------------
    palette_ram_addr_o <= spr_palette_ram_addr_s when state_reg_s = DRAW_SPRITES else layer_palette_ram_addr_s;

    -- Frame buffer memory bus
    --------------------------
    frame_buffer_addr_o      <= spr_frame_buffer_addr_s when state_reg_s = DRAW_SPRITES else
                                clear_frame_buffer_addr_s when state_reg_s = CLEAR_FB else
                                layer_frame_buffer_addr_s;
    frame_buffer_color_o     <= spr_frame_buffer_color_s when state_reg_s = DRAW_SPRITES else
                                clear_frame_buffer_color_s when state_reg_s = CLEAR_FB else
                                layer_frame_buffer_color_s;
    frame_buffer_write_o     <= spr_frame_buffer_write_s when state_reg_s = DRAW_SPRITES else
                                clear_frame_buffer_write_s when state_reg_s = CLEAR_FB else
                                layer_frame_buffer_write_s when (state_reg_s = DRAW_LAYER_0) or (state_reg_s = DRAW_LAYER_1) or (state_reg_s = DRAW_LAYER_2) else
                                '0';

    -- Priority RAM
    ---------------
    priority_ram_block : block
        signal priority_ram_read_data_s  : std_logic_vector(priority_t'range);
        signal priority_ram_read_addr_s  : priority_ram_addr_t;
        signal priority_ram_write_data_s : priority_t;
        signal priority_ram_write_addr_s : priority_ram_addr_t;
        signal priority_ram_write_s      : std_logic;
    begin

        -- Inputs
        priority_ram_read_addr_s  <= spr_priority_ram_read_addr_s when state_reg_s = DRAW_SPRITES else
                                     layer_priority_ram_read_addr_s;
        priority_ram_write_data_s <= spr_priority_ram_write_data_s when state_reg_s = DRAW_SPRITES else
                                     clear_priority_ram_data_s when state_reg_s = CLEAR_FB else
                                     layer_priority_ram_write_data_s;
        priority_ram_write_addr_s <= spr_priority_ram_write_addr_s when state_reg_s = DRAW_SPRITES else
                                     clear_priority_ram_addr_s when state_reg_s = CLEAR_FB else
                                     layer_priority_ram_write_addr_s;
        priority_ram_write_s      <= spr_priority_ram_write_s when state_reg_s = DRAW_SPRITES else
                                     clear_priority_ram_write_s when state_reg_s = CLEAR_FB else
                                     layer_priority_ram_write_s;

        priority_ram : entity work.dual_port_ram
            generic map (
                ADDR_WIDTH_A => DDP_FRAME_BUFFER_ADDR_BITS,
                DATA_WIDTH_A => DDP_FRAME_BUFFER_PRIORITY_BITS,
                ADDR_WIDTH_B => DDP_FRAME_BUFFER_ADDR_BITS,
                DATA_WIDTH_B => DDP_FRAME_BUFFER_PRIORITY_BITS)
            port map (
                clk    => clk_i,
                we_a   => priority_ram_write_s,
                addr_a => priority_ram_write_addr_s,
                din_a  => std_logic_vector(priority_ram_write_data_s),
                addr_b => priority_ram_read_addr_s,
                dout_b => priority_ram_read_data_s);

        -- Outputs
        spr_priority_ram_read_data_s   <= priority_t(priority_ram_read_data_s);
        layer_priority_ram_read_data_s <= priority_t(priority_ram_read_data_s);

    end block priority_ram_block;

end struct;
