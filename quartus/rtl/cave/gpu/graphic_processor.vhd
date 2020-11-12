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
        INCLUDE_LAYER_PROCESOR_G : boolean := true);
    port (
        -- Standard signals
        clk_i                    : in  std_logic;
        rst_i                    : in  std_logic;
        -- Control signals
        generateFrame            : in  std_logic;
        bufferSelect             : in  std_logic;
        -- Tile ROM
        tileRom_rd               : out std_logic;
        tileRom_addr             : out gfx_rom_addr_t;
        tileRom_dout             : in  gfx_rom_data_t;
        tileRom_valid            : in  std_logic;
        tileRom_tinyBurst        : out std_logic;
        tileRom_burstDone        : in  std_logic;
        -- Sprite RAM
        spriteRam_rd             : out std_logic;
        spriteRam_addr           : out sprite_ram_info_access_t;
        spriteRam_dout           : in  sprite_ram_line_t;
        -- Layer 0 RAM
        layer0Ram_rd             : out std_logic;
        layer0Ram_addr           : out layer_ram_info_access_t;
        layer0Ram_dout           : in  layer_ram_line_t;
        -- Layer 1 RAM
        layer1Ram_rd             : out std_logic;
        layer1Ram_addr           : out layer_ram_info_access_t;
        layer1Ram_dout           : in  layer_ram_line_t;
        -- Layer 2 RAM
        layer2Ram_rd             : out std_logic;
        layer2Ram_addr           : out layer_ram_info_access_t;
        layer2Ram_dout           : in  layer_ram_line_t;
        -- Layer 0 info
        layer0Info_rd            : out std_logic;
        layer0Info_addr          : out unsigned(0 downto 0);
        layer0Info_dout          : in  layer_info_line_t;
        -- Layer 1 info
        layer1Info_rd            : out std_logic;
        layer1Info_addr          : out unsigned(0 downto 0);
        layer1Info_dout          : in  layer_info_line_t;
        -- Layer 2 info
        layer2Info_rd            : out std_logic;
        layer2Info_addr          : out unsigned(0 downto 0);
        layer2Info_dout          : in  layer_info_line_t;
        -- Palette RAM
        paletteRam_rd             : out std_logic;
        paletteRam_addr           : out unsigned(14 downto 0);
        paletteRam_dout           : in  word_t;
        -- Frame Buffer
        frameBuffer_wr           : out std_logic;
        frameBuffer_addr         : out frame_buffer_addr_t;
        frameBuffer_mask         : out std_logic_vector(1 downto 0);
        frameBuffer_din          : out std_logic_vector(DDP_WORD_WIDTH-2 downto 0);
        frameBuffer_dmaStart     : out std_logic;
        frameBuffer_dmaDone      : in  std_logic);
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
    signal frame_buffer_color_s            : color_t;
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

    spriteRam_rd <= '1';
    layer0Ram_rd <= '1';
    layer1Ram_rd <= '1';
    layer2Ram_rd <= '1';
    layer0Info_rd <= '1';
    layer1Info_rd <= '1';
    layer2Info_rd <= '1';
    paletteRam_rd <= '1';
    layer0Info_addr <= "0";
    layer1Info_addr <= "0";
    layer2Info_addr <= "0";
    frameBuffer_mask <= "11";

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
                if generateFrame = '1' then
                    next_state_s <= CLEAR_FB;
                end if;

            when CLEAR_FB =>
                if clear_fb_done_s = '1' then
                    next_state_s <= DRAW_SPRITES;
                end if;

            when DRAW_SPRITES =>
                if sprite_processor_done_s = '1' then
                    next_state_s <= DRAW_LAYER_0;
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
                if frameBuffer_dmaDone = '1' then
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

    frameBuffer_dmaStart <= '1' when state_reg_s = START_DMA else '0';

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
            buffer_select_i           => bufferSelect,
            done_o                    => sprite_processor_done_s,
            --
            sprite_ram_addr_o         => spriteRam_addr,
            sprite_ram_info_i         => spriteRam_dout,
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
            palette_ram_data_i        => paletteRam_dout,
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
                    palette_ram_data_i        => paletteRam_dout,
                    frame_buffer_addr_o       => layer_frame_buffer_addr_s,
                    frame_buffer_color_o      => layer_frame_buffer_color_s,
                    frame_buffer_write_o      => layer_frame_buffer_write_s);

            -- Global Layer Info Mux
            layer_info_to_layer_processor_s <= extract_global_layer_info_from_regs(layer0Info_dout) when state_reg_s = DRAW_LAYER_0 else
                                               extract_global_layer_info_from_regs(layer1Info_dout) when state_reg_s = DRAW_LAYER_1 else
                                               extract_global_layer_info_from_regs(layer2Info_dout);

            -- All get the same address, only one is used at a time
            layer0Ram_addr <= layer_ram_addr_s;
            layer1Ram_addr <= layer_ram_addr_s;
            layer2Ram_addr <= layer_ram_addr_s;

            -- Layer RAM Info Mux
            layer_ram_info_s <= layer0Ram_dout when state_reg_s = DRAW_LAYER_0 else
                                layer1Ram_dout when state_reg_s = DRAW_LAYER_1 else
                                layer2Ram_dout;

            -- Get the correct layer rom address
            layer_rom_addr_s <= layer_processor_rom_offset_s + layer_0_rom_offset_c when state_reg_s = DRAW_LAYER_0 else
                                layer_processor_rom_offset_s + layer_1_rom_offset_c when state_reg_s = DRAW_LAYER_1 else
                                layer_processor_rom_offset_s + layer_2_rom_offset_c;

        end block layer_processor_block;

    else generate

        -- No layer processor
        layer0Ram_addr                  <= (others => '0');
        layer1Ram_addr                  <= (others => '0');
        layer2Ram_addr                  <= (others => '0');
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

    tileRom_addr             <= spr_rom_addr_s when state_reg_s = DRAW_SPRITES else
                                layer_rom_addr_s when (state_reg_s = DRAW_LAYER_0) or (state_reg_s = DRAW_LAYER_1) or (state_reg_s = DRAW_LAYER_2) else
                                (others => '0');
    tileRom_tinyBurst        <= '0' when state_reg_s = DRAW_SPRITES else tiny_burst_s;
    tileRom_rd               <= spr_burst_read_s when state_reg_s = DRAW_SPRITES else layer_burst_read_s;
    spr_data_s               <= tileRom_dout;
    layer_data_s             <= tileRom_dout;
    spr_data_valid_s         <= tileRom_valid when state_reg_s = DRAW_SPRITES else '0';
    layer_data_valid_s       <= tileRom_valid when (state_reg_s = DRAW_LAYER_0) or (state_reg_s = DRAW_LAYER_1) or (state_reg_s = DRAW_LAYER_2) else '0';
    spr_data_burst_done_s    <= tileRom_burstDone when state_reg_s = DRAW_SPRITES else '0';
    layer_data_burst_done_s  <= tileRom_burstDone when (state_reg_s = DRAW_LAYER_0) or (state_reg_s = DRAW_LAYER_1) or (state_reg_s = DRAW_LAYER_2) else '0';

    -- Palette memory bus
    ---------------------
    paletteRam_addr <= spr_palette_ram_addr_s when state_reg_s = DRAW_SPRITES else layer_palette_ram_addr_s;

    -- Frame buffer memory bus
    --------------------------
    frameBuffer_wr       <= spr_frame_buffer_write_s when state_reg_s = DRAW_SPRITES else
                            clear_frame_buffer_write_s when state_reg_s = CLEAR_FB else
                            layer_frame_buffer_write_s when (state_reg_s = DRAW_LAYER_0) or (state_reg_s = DRAW_LAYER_1) or (state_reg_s = DRAW_LAYER_2) else
                            '0';
    frameBuffer_addr     <= spr_frame_buffer_addr_s when state_reg_s = DRAW_SPRITES else
                            clear_frame_buffer_addr_s when state_reg_s = CLEAR_FB else
                            layer_frame_buffer_addr_s;
    frame_buffer_color_s <= spr_frame_buffer_color_s when state_reg_s = DRAW_SPRITES else
                            clear_frame_buffer_color_s when state_reg_s = CLEAR_FB else
                            layer_frame_buffer_color_s;

    frameBuffer_din <= frame_buffer_color_s.r & frame_buffer_color_s.g & frame_buffer_color_s.b;

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
