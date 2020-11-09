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
-- File         : cave_pkg.vhd
-- Description  :
--
-- Author       : Rick Wertenbroek
-- Version      : 0.0
--
-- Dependencies : log_pkg.vhd
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;
use work.log_pkg.all;

package cave_pkg is

    -- Warning all positions below for the memory assume 16-bit Big-Endian.
    -- So byte 0 is the MSB of a word and byte 1 is the LSB of a word.
    -- Since the BRAMs are an array of words we don't have to worry about this,
    -- plus the word writes to RAM must be aligned.
    -- On the HDL side we only access the BRAMs via words or lines of words so
    -- no need to worry about endianness. (it's only if you want to compare
    -- with memory dumps from e.g. MAME).

-------------------
    -- Constants --
    --------------------------------------------------------------------------------
    -- These are constants not generics, they are not supposed to be changed !
    -- ever !
    --
    -- Or assume the consequences and rework everything that broke.

    -------------
    -- General --
    -------------
    constant DDP_NIBBLE_WIDTH                : natural := 4;
    constant DDP_BYTE_WIDTH                  : natural := 8;
    constant DDP_WORD_WIDTH                  : natural := 16;

    -----------------
    -- PROGRAM ROM --
    -----------------
    constant DDP_ROM_LOG_SIZE_C              : natural := 20;
    constant DDP_ROM_CACHE_LINE_WORDS        : natural := 16;
    constant DDP_ROM_CACHE_LINE_WIDTH        : natural := DDP_WORD_WIDTH * DDP_ROM_CACHE_LINE_WORDS;

    --------------------
    -- Screen related --
    --------------------
    constant DDP_COLOR_DEPTH_R               : natural := 5;
    constant DDP_COLOR_DEPTH_G               : natural := 5;
    constant DDP_COLOR_DEPTH_B               : natural := 5;

    constant DDP_VISIBLE_SCREEN_WIDTH        : natural := 320;
    constant DDP_VISIBLE_SCREEN_HEIGHT       : natural := 240;
    constant DDP_FRAME_BUFFER_ADDR_BITS_X    : natural := ilogup(DDP_VISIBLE_SCREEN_WIDTH);
    constant DDP_FRAME_BUFFER_ADDR_BITS_Y    : natural := ilogup(DDP_VISIBLE_SCREEN_HEIGHT);
    constant DDP_FRAME_BUFFER_ADDR_BITS      : natural := DDP_FRAME_BUFFER_ADDR_BITS_X+DDP_FRAME_BUFFER_ADDR_BITS_Y;
    constant DDP_FRAME_BUFFER_COLOR_BITS     : natural := 15;
    constant DDP_FRAME_BUFFER_PRIORITY_BITS  : natural := 2;
    constant DDP_MAX_SPRITES_ON_SCREEN       : natural := 1024;
    constant DDP_BYTES_PER_8x8_TILE          : natural := 8*8;
    constant DDP_BYTES_PER_16x16_TILE        : natural := 16*8;

    -----------------
    -- Palette RAM --
    -----------------
    constant DDP_PALETTE_RAM_ADDR_WIDTH      : natural := 16-1; -- The palette ram is 64kBytes
                                                                -- but we access as
                                                                -- words from HDL
    constant DDP_PALETTE_TOTAL               : natural := 128; -- Number of
                                                               -- palettes in
                                                               -- palette RAM
    constant DDP_PALETTE_COLORS              : natural := 256; -- Colors per palette
    constant DDP_PALETTE_SELECT_BITS         : natural := ilogup(DDP_PALETTE_TOTAL);
    constant DDP_PALETTE_COLOR_SELECT_BITS   : natural := ilogup(DDP_PALETTE_COLORS);

    ----------------
    -- Sprite RAM --
    ----------------
    constant DDP_SPRITE_RAM_INFO_WORDS       : natural := 8;
    constant DDP_SPRITE_RAM_LINE_WIDTH       : natural := DDP_SPRITE_RAM_INFO_WORDS * DDP_WORD_WIDTH;
    constant DDP_SPRITE_RAM_LINE_ADDR_WIDTH  : natural := 12;


    -- The first word in a sprite RAM line is
    -- NA[15:14] COLOR_CODE[13:8] NA[7:6] PRIO [5:4] FLIP_X [3] FLIP_Y[2] CODE_HI[1:0]
    constant DDP_SPRITE_COLOR_CODE_WORD_POS  : natural := 0;
    constant DDP_SPRITE_PRIO_WORD_POS        : natural := 0;
    constant DDP_SPRITE_FLIP_X_WORD_POS      : natural := 0;
    constant DDP_SPRITE_FLIP_Y_WORD_POS      : natural := 0;
    constant DDP_SPRITE_CODE_HI_WORD_POS     : natural := 0;

    constant DDP_SPRITE_COLOR_CODE_HI_POS    : natural := 13;
    constant DDP_SPRITE_COLOR_CODE_LO_POS    : natural := 8;
    constant DDP_SPRITE_COLOR_CODE_WIDTH     : natural := DDP_SPRITE_COLOR_CODE_HI_POS - DDP_SPRITE_COLOR_CODE_LO_POS + 1;

    constant DDP_SPRITE_PRIO_HI_POS          : natural := 5;
    constant DDP_SPRITE_PRIO_LO_POS          : natural := 4;
    constant DDP_SPRITE_PRIO_WIDTH           : natural := DDP_SPRITE_PRIO_HI_POS - DDP_SPRITE_PRIO_LO_POS + 1;

    constant DDP_SPRITE_FLIP_X_POS           : natural := 3;

    constant DDP_SPRITE_FLIP_Y_POS           : natural := 2;

    constant DDP_SPRITE_CODE_HI_HI_POS       : natural := 1;
    constant DDP_SPRITE_CODE_HI_LO_POS       : natural := 0;
    constant DDP_SPRITE_CODE_HI_WIDTH        : natural := DDP_SPRITE_CODE_HI_HI_POS - DDP_SPRITE_CODE_HI_LO_POS + 1;

    -- The second word in a sprite RAM line is
    -- CODE_LO [15:0]
    constant DDP_SPRITE_CODE_LO_WORD_POS     : natural := 1;

    constant DDP_SPRITE_CODE_LO_HI_POS       : natural := 15;
    constant DDP_SPRITE_CODE_LO_LO_POS       : natural := 0;
    constant DDP_SPRITE_CODE_LO_WIDTH        : natural := DDP_SPRITE_CODE_LO_HI_POS - DDP_SPRITE_CODE_LO_LO_POS + 1;

    constant DDP_SPRITE_CODE_WIDTH           : natural := DDP_SPRITE_CODE_HI_WIDTH + DDP_SPRITE_CODE_LO_WIDTH;

    -- The third word in a sprite RAM line is
    -- X_POSITION [15:0] (not all bits are used but the unused MSB are set to 0)
    -- I never saw a value above 0x02A0 and this is a special value to indicate
    -- thate there is no sprite for this line
    constant DDP_SPRITE_POS_X_WORD_POS       : natural := 2;

    constant DDP_SPRITE_POS_X_HI_POS         : natural := 15; -- This could be reduced
    constant DDP_SPRITE_POS_X_LO_POS         : natural := 0;
    constant DDP_SPRITE_POS_X_WIDTH          : natural := DDP_SPRITE_POS_X_HI_POS - DDP_SPRITE_POS_X_LO_POS + 1;

    -- The fourth word in a sprite RAM line is
    -- Y_POSITION [15:0] (not all bits are used, same as above)
    constant DDP_SPRITE_POS_Y_WORD_POS       : natural := 3;

    constant DDP_SPRITE_POS_Y_HI_POS         : natural := 15; -- This could be reduced
    constant DDP_SPRITE_POS_Y_LO_POS         : natural := 0;
    constant DDP_SPRITE_POS_Y_WIDTH          : natural := DDP_SPRITE_POS_Y_HI_POS - DDP_SPRITE_POS_Y_LO_POS + 1;

    -- The fifth word in a sprite RAM line is
    -- TILE_SIZE_X [15:8] TILE_SIZE_Y [7:0]
    constant DDP_SPRITE_TILE_SIZE_X_WORD_POS : natural := 4;
    constant DDP_SPRITE_TILE_SIZE_Y_WORD_POS : natural := 4;

    constant DDP_SPRITE_TILE_SIZE_X_HI_POS   : natural := 15;
    constant DDP_SPRITE_TILE_SIZE_X_LO_POS   : natural := 8;
    constant DDP_SPRITE_TILE_SIZE_X_WIDTH    : natural := DDP_SPRITE_TILE_SIZE_X_HI_POS - DDP_SPRITE_TILE_SIZE_X_LO_POS + 1;

    constant DDP_SPRITE_TILE_SIZE_Y_HI_POS   : natural := 7;
    constant DDP_SPRITE_TILE_SIZE_Y_LO_POS   : natural := 0;
    constant DDP_SPRITE_TILE_SIZE_Y_WIDTH    : natural := DDP_SPRITE_TILE_SIZE_Y_HI_POS - DDP_SPRITE_TILE_SIZE_Y_LO_POS + 1;

    -- The sixth word in a sprite RAM line is
    -- NA - Not Assigned

    -- The seventh word in a sprite RAM line is
    -- ZOOM_X [15:0] -- Not sure if used and how
    constant DDP_SPRITE_ZOOM_X_WORD_POS      : natural := 6;

    constant DDP_SPRITE_ZOOM_X_HI_POS        : natural := 15;
    constant DDP_SPRITE_ZOOM_X_LO_POS        : natural := 0;
    constant DDP_SPRITE_ZOOM_X_WIDTH         : natural := DDP_SPRITE_ZOOM_X_HI_POS - DDP_SPRITE_ZOOM_X_LO_POS + 1;

    -- The eigth word in a sprite RAM line is
    -- ZOOM_Y [15:0] -- Not sure if used and how
    constant DDP_SPRITE_ZOOM_Y_WORD_POS      : natural := 7;

    constant DDP_SPRITE_ZOOM_Y_HI_POS        : natural := 15;
    constant DDP_SPRITE_ZOOM_Y_LO_POS        : natural := 0;
    constant DDP_SPRITE_ZOOM_Y_WIDTH         : natural := DDP_SPRITE_ZOOM_Y_HI_POS - DDP_SPRITE_ZOOM_Y_LO_POS + 1;

    ---------------
    -- Layer RAM --
    ---------------
    constant DDP_LAYER_TILE_RAM_INFO_WORDS      : natural := 2;
    constant DDP_LAYER_TILE_RAM_LINE_WIDTH      : natural := DDP_LAYER_TILE_RAM_INFO_WORDS * DDP_WORD_WIDTH;
    constant DDP_LAYER_TILE_RAM_LINE_ADDR_WIDTH : natural := 14; -- Max 64kB, two words, 4 bytes per line

    -- Tile info is two words in layer RAM
    -- The first word in a layer tile info line is
    -- PRIO[15:14] COLOR_CODE[13:8] CODE_HI[7:0]
    constant DDP_LAYER_TILE_PRIO_WORD_POS       : natural := 0;
    constant DDP_LAYER_TILE_PRIO_HI_POS         : natural := 15;
    constant DDP_LAYER_TILE_PRIO_LO_POS         : natural := 14;
    constant DDP_LAYER_TILE_PRIO_WIDTH          : natural := DDP_LAYER_TILE_PRIO_HI_POS - DDP_LAYER_TILE_PRIO_LO_POS + 1;

    constant DDP_LAYER_TILE_COLOR_CODE_WORD_POS : natural := 0;
    constant DDP_LAYER_TILE_COLOR_CODE_HI_POS   : natural := 13;
    constant DDP_LAYER_TILE_COLOR_CODE_LO_POS   : natural := 8;
    constant DDP_LAYER_TILE_COLOR_CODE_WIDTH    : natural := DDP_LAYER_TILE_COLOR_CODE_HI_POS - DDP_LAYER_TILE_COLOR_CODE_LO_POS + 1;

    constant DDP_LAYER_TILE_CODE_HI_WORD_POS    : natural := 0;
    constant DDP_LAYER_TILE_CODE_HI_HI_POS      : natural := 7;
    constant DDP_LAYER_TILE_CODE_HI_LO_POS      : natural := 0;
    constant DDP_LAYER_TILE_CODE_HI_WIDTH       : natural := DDP_LAYER_TILE_CODE_HI_HI_POS - DDP_LAYER_TILE_CODE_HI_LO_POS + 1;

    -- The second word in a layer tile info line is
    -- CODE_LO [15:0]
    constant DDP_LAYER_TILE_CODE_LO_WORD_POS    : natural := 1;
    constant DDP_LAYER_TILE_CODE_LO_HI_POS      : natural := 15;
    constant DDP_LAYER_TILE_CODE_LO_LO_POS      : natural := 0;
    constant DDP_LAYER_TILE_CODE_LO_WIDTH       : natural := DDP_LAYER_TILE_CODE_LO_HI_POS - DDP_LAYER_TILE_CODE_LO_LO_POS + 1;

    constant DDP_LAYER_TILE_CODE_WIDTH          : natural := DDP_LAYER_TILE_CODE_HI_WIDTH + DDP_LAYER_TILE_CODE_LO_WIDTH;

    ----------------
    -- Layer Regs --
    ----------------
    constant DDP_LAYER_INFO_WORDS               : natural := 3;
    constant DDP_LAYER_INFO_LINE_WIDTH          : natural := DDP_LAYER_INFO_WORDS * DDP_WORD_WIDTH;

    -- The first word is
    -- FLIP_X_EN (active low) [15] SCROLL_EN [14] NA [13:9] SCROLL_X [8:0]
    constant DDP_LAYER_FLIP_X_EN_WORD_POS             : natural := 0;
    constant DDP_LAYER_FLIP_X_EN_BIT_POS              : natural := 15;
    constant DDP_LAYER_ROW_SCROLL_EN_WORD_POS         : natural := 0;
    constant DDP_LAYER_ROW_SCROLL_EN_BIT_POS          : natural := 14;
    -- I was wrong obviously
    --constant DDP_LAYER_ROW_SCROLL_EN_BIT_POS          : natural := 9;
    constant DDP_LAYER_SCROLL_X_WORD_POS              : natural := 0;
    constant DDP_LAYER_SCROLL_X_HI_POS                : natural := 8;
    constant DDP_LAYER_SCROLL_X_LO_POS                : natural := 0;

    -- The second word is
    -- FLIP_Y_EN (active low) [15] ROW_SELECT_EN [14] TILE_SIZE [13] NA [12:9] SCROLL_Y [8:0]
    -- Tile size 0 is 8x8 and 1 is 16x16
    constant DDP_LAYER_FLIP_Y_EN_WORD_POS             : natural := 1;
    constant DDP_LAYER_FLIP_Y_EN_BIT_POS              : natural := 15;
    constant DDP_LAYER_ROW_SELECT_EN_WORD_POS         : natural := 1;
    constant DDP_LAYER_ROW_SELECT_EN_BIT_POS          : natural := 14;
    -- I was wrong obviously
    --constant DDP_LAYER_ROW_SELECT_EN_BIT_POS          : natural := 9;
    constant DDP_LAYER_TILE_SIZE_WORD_POS             : natural := 1;
    constant DDP_LAYER_TILE_SIZE_BIT_POS              : natural := 13;
    constant DDP_LAYER_SCROLL_Y_WORD_POS              : natural := 1;
    constant DDP_LAYER_SCROLL_Y_HI_POS                : natural := 8;
    constant DDP_LAYER_SCROLL_Y_LO_POS                : natural := 0;

    -- The third word is
    -- NA [15:5] LAYER_DISABLE [4] NA [3:2] LAYER_PRIORITY [1:0]
    constant DDP_LAYER_LAYER_DISABLE_WORD_POS         : natural := 2;
    constant DDP_LAYER_LAYER_DISABLE_BIT_POS          : natural := 4;
    constant DDP_LAYER_LAYER_PRIO_WORD_POS            : natural := 2;
    constant DDP_LAYER_LAYER_PRIO_HI_POS              : natural := 1;
    constant DDP_LAYER_LAYER_PRIO_LO_POS              : natural := 0;

    -- Several registers are used to set the layer info (layer global settings)
    -- A 16-bit register per layer (games have up to 4 layers, Dodonpachi has 3)
    constant DDP_LAYER_PRIO_WIDTH               : natural := 2;
    constant DDP_LAYER_SCROLL_X_WIDTH           : natural := 9; -- TODO !
    constant DDP_LAYER_SCROLL_Y_WIDTH           : natural := 9; -- TODO !


---------------
    -- Types --
    --------------------------------------------------------------------------------
    subtype nibble_t is std_logic_vector(DDP_NIBBLE_WIDTH-1 downto 0);
    subtype byte_t is std_logic_vector(DDP_BYTE_WIDTH-1 downto 0);
    subtype word_t is std_logic_vector(DDP_WORD_WIDTH-1 downto 0);

    -- 2D vector
    type vec2_t is record
        x : unsigned(8 downto 0);
        y : unsigned(8 downto 0);
    end record vec2_t;

    -- Video signals
    type video_t is record
        -- Position
        pos : vec2_t;

        -- Sync signals
        hsync : std_logic;
        vsync : std_logic;

        -- Blanking signals
        hblank : std_logic;
        vblank : std_logic;

        -- Enable video output
        enable : std_logic;
    end record video_t;

    -- Sprites and layers use 16 colors tiles (coded on nibbles, 4-bit)
    subtype code_16_colors_t is std_logic_vector(DDP_NIBBLE_WIDTH-1 downto 0);

    -- layers can be 256 colors per tile (coded on bytes, 8-bit)
    subtype code_256_colors_t is std_logic_vector(DDP_BYTE_WIDTH-1 downto 0);

    -- This is the address width to access the sprite RAM by line (8x16bit words)
    subtype sprite_ram_info_access_t is unsigned(DDP_SPRITE_RAM_LINE_ADDR_WIDTH-1 downto 0);

    -- This is the address width to access the layer RAM by line
    subtype layer_ram_info_access_t is unsigned(DDP_LAYER_TILE_RAM_LINE_ADDR_WIDTH-1 downto 0);

    -- The binary sprite ram line (to be read from the dual ported BRAM on HDL
    -- side, the 68k side is a 16-bit RAM interface)
    subtype sprite_ram_line_t is std_logic_vector(DDP_SPRITE_RAM_LINE_WIDTH-1 downto 0);

    -- The binary layer ram line (to be read from the dual ported BRAM on HDL
    -- side, the 68k side is a 16-bit RAM interface)
    subtype layer_ram_line_t is std_logic_vector(DDP_LAYER_TILE_RAM_LINE_WIDTH-1 downto 0);

    -- The binary global layer line (concatenation of vctrl_X regs)
    subtype layer_info_line_t is std_logic_vector(DDP_LAYER_INFO_LINE_WIDTH-1 downto 0);

    -- Graphics ROM data
    subtype gfx_rom_data_t is std_logic_vector(63 downto 0);

    -- Graphics ROM address
    subtype gfx_rom_addr_t is unsigned(31 downto 0);

    -- This contains all the information related to a sprite (will be extracted
    -- from a sprite RAM line which is 8 words).
    type sprite_info_t is record
        priority    : unsigned(DDP_SPRITE_PRIO_WIDTH-1 downto 0);
        color_code  : unsigned(DDP_SPRITE_COLOR_CODE_WIDTH-1 downto 0);
        code        : unsigned(DDP_SPRITE_CODE_WIDTH-1 downto 0);
        flip_x      : std_logic;
        flip_y      : std_logic;
        pos_x       : unsigned(DDP_SPRITE_POS_X_WIDTH-1 downto 0);
        pos_y       : unsigned(DDP_SPRITE_POS_Y_WIDTH-1 downto 0);
        tile_size_x : unsigned(DDP_SPRITE_TILE_SIZE_X_WIDTH-1 downto 0);
        tile_size_y : unsigned(DDP_SPRITE_TILE_SIZE_Y_WIDTH-1 downto 0);
        zoom_x      : unsigned(DDP_SPRITE_ZOOM_X_WIDTH-1 downto 0);
        zoom_y      : unsigned(DDP_SPRITE_ZOOM_Y_WIDTH-1 downto 0);
    end record sprite_info_t;

    -- This contains the information related to a layer tile (will be extracted
    -- from a layer RAM line which is 2 words).
    type tile_info_t is record
        priority   : unsigned(DDP_LAYER_TILE_PRIO_WIDTH-1 downto 0);
        color_code : unsigned(DDP_LAYER_TILE_COLOR_CODE_WIDTH-1 downto 0);
        code       : unsigned(DDP_LAYER_TILE_CODE_WIDTH-1 downto 0);
    end record tile_info_t;

    -- These are settings that apply to a whole layer
    type layer_info_t is record
        priority           : unsigned(DDP_LAYER_PRIO_WIDTH-1 downto 0);
        small_tile         : std_logic;
        disabled           : std_logic;
        flip_x             : std_logic;
        flip_y             : std_logic;
        row_scroll_enabled : std_logic;
        row_select_enabled : std_logic;
        scroll_x           : unsigned(DDP_LAYER_SCROLL_X_WIDTH-1 downto 0);
        scroll_y           : unsigned(DDP_LAYER_SCROLL_Y_WIDTH-1 downto 0);
    end record layer_info_t;

    -- Cave 1st Gen uses RGB555 Colors (GRB555 in memory)
    type color_t is record
        r : std_logic_vector(DDP_COLOR_DEPTH_R-1 downto 0);
        g : std_logic_vector(DDP_COLOR_DEPTH_G-1 downto 0);
        b : std_logic_vector(DDP_COLOR_DEPTH_B-1 downto 0);
    end record color_t;

    -- Cave 1st Gen uses 4 levels of priority
    subtype priority_t is unsigned(DDP_FRAME_BUFFER_PRIORITY_BITS-1 downto 0);

    -- Address to access the 320x256 (320x240 visible) frame buffer
    subtype frame_buffer_addr_t is unsigned(DDP_FRAME_BUFFER_ADDR_BITS-1 downto 0);
    subtype priority_ram_addr_t is frame_buffer_addr_t;

    -- Note : It should be great to have 2 Frame Buffers with dual buffering
    -- but this would require about 8MBits which is way to much for BRAM. The
    -- actual DoDonPachi PCB has two 4MBit chips for the frame buffer so I
    -- expect two frame buffers with double buffering, allowing to show the
    -- frames when completed or at least remain on the same frame while
    -- building the new one. I hope that my lack of memory will be compensated
    -- by speed (MHz FPGA fabric > MHz DDP).
    -- The actual chips on the PCB are : GM71C4260CJ-70 (4M, 256Kx16, 70ns)

    -- The palette RAM holds the real (effective) colors set by the CPU
    subtype palette_ram_addr_t is unsigned(DDP_PALETTE_RAM_ADDR_WIDTH-1 downto 0);

    subtype palette_ram_data_t is word_t;

    -- A more convenient way to select the color
    type palette_color_select_t is record
        palette : unsigned(DDP_PALETTE_SELECT_BITS-1 downto 0);
        color   : unsigned(DDP_PALETTE_COLOR_SELECT_BITS-1 downto 0);
    end record palette_color_select_t;

    -------------------
    -- Functions --
    --------------------------------------------------------------------------------

    function extract_nibble(data : std_logic_vector; n : natural) return nibble_t;

    -- Extract the color from the palette word
    function extract_color_from_palette_data(palette_data_i : palette_ram_data_t) return color_t;

    -- Extract the sprite information from a sprite RAM line
    function extract_sprite_info_from_sprite_ram_line(sprite_ram_line_i : sprite_ram_line_t) return sprite_info_t;

    -- Extract the tile information from a layer RAM line
    function extract_tile_info_from_layer_ram_line(layer_ram_line_i : layer_ram_line_t) return tile_info_t;

    -- Extract the global layer info
    function extract_global_layer_info_from_regs(layer_info_line_i : layer_info_line_t) return layer_info_t;

    function palette_ram_addr_from_palette_color_select(palette_color_select_i : palette_color_select_t) return palette_ram_addr_t;

end cave_pkg;

package body cave_pkg is

    function extract_nibble(data : std_logic_vector; n : natural) return nibble_t is
    begin
        return data((n+1)*nibble_t'length-1 downto n*nibble_t'length);
    end extract_nibble;

    -- Extract the color from the palette word
    function extract_color_from_palette_data(palette_data_i : palette_ram_data_t) return color_t is
        variable color_v : color_t;
    begin
        -- GRB555row_scroll_en
        color_v.g := palette_data_i(DDP_COLOR_DEPTH_R+DDP_COLOR_DEPTH_G+DDP_COLOR_DEPTH_B-1 downto DDP_COLOR_DEPTH_G+DDP_COLOR_DEPTH_B);
        color_v.r := palette_data_i(DDP_COLOR_DEPTH_G+DDP_COLOR_DEPTH_B-1 downto DDP_COLOR_DEPTH_B);
        color_v.b := palette_data_i(DDP_COLOR_DEPTH_B-1 downto 0);

        return color_v;
    end extract_color_from_palette_data;

    -- The sprite ram line separated in words
    type sprite_ram_line_words_t is array (DDP_SPRITE_RAM_INFO_WORDS-1 downto 0) of word_t;

    -- The layer tile ram line separated in words
    type layer_tile_ram_line_words_t is array (DDP_LAYER_TILE_RAM_INFO_WORDS-1 downto 0) of word_t;

    -- The layer info line separated in words
    type layer_info_words_t is array (DDP_LAYER_INFO_WORDS-1 downto 0) of word_t;

    -- Function to separate a sprite ram line into an array of words
    function sprite_ram_line_words_from_sprite_ram_line(sprite_ram_line_i : sprite_ram_line_t) return sprite_ram_line_words_t is
        variable sprite_ram_line_words_v : sprite_ram_line_words_t;
    begin
        for i in 0 to DDP_SPRITE_RAM_INFO_WORDS-1 loop
            sprite_ram_line_words_v(i) := sprite_ram_line_i((i+1)*DDP_WORD_WIDTH-1 downto i*DDP_WORD_WIDTH);
        end loop;

        return sprite_ram_line_words_v;
    end sprite_ram_line_words_from_sprite_ram_line;

    -- Function to separate a layer tile ram line into an array of words
    function layer_tile_ram_line_words_from_layer_ram_line(layer_ram_line_i : layer_ram_line_t) return layer_tile_ram_line_words_t is
        variable layer_tile_ram_line_words_v : layer_tile_ram_line_words_t;
    begin
        for i in 0 to DDP_LAYER_TILE_RAM_INFO_WORDS-1 loop
            layer_tile_ram_line_words_v(i) := layer_ram_line_i((i+1)*DDP_WORD_WIDTH-1 downto i*DDP_WORD_WIDTH);
        end loop;

        return layer_tile_ram_line_words_v;
    end layer_tile_ram_line_words_from_layer_ram_line;

    -- Function to separate a layer info line into an array of words
    function layer_info_words_from_layer_info_line(layer_info_line_i : layer_info_line_t) return layer_info_words_t is
        variable layer_info_words_v : layer_info_words_t;
    begin
        for i in 0 to DDP_LAYER_INFO_WORDS-1 loop
            layer_info_words_v(i) := layer_info_line_i((i+1)*DDP_WORD_WIDTH-1 downto i*DDP_WORD_WIDTH);
        end loop;

        return layer_info_words_v;
    end layer_info_words_from_layer_info_line;

    -- This is only cable management ... (very not so fun to do)
    function extract_sprite_info_from_sprite_ram_line(sprite_ram_line_i : sprite_ram_line_t) return sprite_info_t is
        variable sprite_info_v  : sprite_info_t;
        constant sprite_words_c : sprite_ram_line_words_t := sprite_ram_line_words_from_sprite_ram_line(sprite_ram_line_i);
        variable code_hi_v      : unsigned(DDP_SPRITE_CODE_HI_WIDTH-1 downto 0);
        variable code_lo_v      : unsigned(DDP_SPRITE_CODE_LO_WIDTH-1 downto 0);
    begin
        sprite_info_v.priority    := unsigned(sprite_words_c(DDP_SPRITE_PRIO_WORD_POS)(DDP_SPRITE_PRIO_HI_POS downto DDP_SPRITE_PRIO_LO_POS));
        sprite_info_v.color_code  := unsigned(sprite_words_c(DDP_SPRITE_COLOR_CODE_WORD_POS)(DDP_SPRITE_COLOR_CODE_HI_POS downto DDP_SPRITE_COLOR_CODE_LO_POS));
        code_hi_v                 := unsigned(sprite_words_c(DDP_SPRITE_CODE_HI_WORD_POS)(DDP_SPRITE_CODE_HI_HI_POS downto DDP_SPRITE_CODE_HI_LO_POS));
        code_lo_v                 := unsigned(sprite_words_c(DDP_SPRITE_CODE_LO_WORD_POS)(DDP_SPRITE_CODE_LO_HI_POS downto DDP_SPRITE_CODE_LO_LO_POS));
        sprite_info_v.code        := code_hi_v & code_lo_v;
        sprite_info_v.flip_x      := sprite_words_c(DDP_SPRITE_FLIP_X_WORD_POS)(DDP_SPRITE_FLIP_X_POS);
        sprite_info_v.flip_y      := sprite_words_c(DDP_SPRITE_FLIP_Y_WORD_POS)(DDP_SPRITE_FLIP_Y_POS);
        sprite_info_v.pos_x       := unsigned(sprite_words_c(DDP_SPRITE_POS_X_WORD_POS)(DDP_SPRITE_POS_X_HI_POS downto DDP_SPRITE_POS_X_LO_POS));
        sprite_info_v.pos_y       := unsigned(sprite_words_c(DDP_SPRITE_POS_Y_WORD_POS)(DDP_SPRITE_POS_Y_HI_POS downto DDP_SPRITE_POS_Y_LO_POS));
        sprite_info_v.tile_size_x := unsigned(sprite_words_c(DDP_SPRITE_TILE_SIZE_X_WORD_POS)(DDP_SPRITE_TILE_SIZE_X_HI_POS downto DDP_SPRITE_TILE_SIZE_X_LO_POS));
        sprite_info_v.tile_size_y := unsigned(sprite_words_c(DDP_SPRITE_TILE_SIZE_Y_WORD_POS)(DDP_SPRITE_TILE_SIZE_Y_HI_POS downto DDP_SPRITE_TILE_SIZE_Y_LO_POS));
        sprite_info_v.zoom_x      := unsigned(sprite_words_c(DDP_SPRITE_ZOOM_X_WORD_POS)(DDP_SPRITE_ZOOM_X_HI_POS downto DDP_SPRITE_ZOOM_X_LO_POS));
        sprite_info_v.zoom_y      := unsigned(sprite_words_c(DDP_SPRITE_ZOOM_Y_WORD_POS)(DDP_SPRITE_ZOOM_Y_HI_POS downto DDP_SPRITE_ZOOM_Y_LO_POS));

        return sprite_info_v;
    end extract_sprite_info_from_sprite_ram_line;

    -- This is only cable management ...
    function extract_tile_info_from_layer_ram_line(layer_ram_line_i : layer_ram_line_t) return tile_info_t is
        variable tile_info_v  : tile_info_t;
        constant tile_words_c : layer_tile_ram_line_words_t := layer_tile_ram_line_words_from_layer_ram_line(layer_ram_line_i);
        variable code_hi_v    : unsigned(DDP_LAYER_TILE_CODE_HI_WIDTH-1 downto 0);
        variable code_lo_v    : unsigned(DDP_LAYER_TILE_CODE_LO_WIDTH-1 downto 0);
    begin
        tile_info_v.priority   := unsigned(tile_words_c(DDP_LAYER_TILE_PRIO_WORD_POS)(DDP_LAYER_TILE_PRIO_HI_POS downto DDP_LAYER_TILE_PRIO_LO_POS));
        tile_info_v.color_code := unsigned(tile_words_c(DDP_LAYER_TILE_COLOR_CODE_WORD_POS)(DDP_LAYER_TILE_COLOR_CODE_HI_POS downto DDP_LAYER_TILE_COLOR_CODE_LO_POS));
        code_hi_v              := unsigned(tile_words_c(DDP_LAYER_TILE_CODE_HI_WORD_POS)(DDP_LAYER_TILE_CODE_HI_HI_POS downto DDP_LAYER_TILE_CODE_HI_LO_POS));
        code_lo_v              := unsigned(tile_words_c(DDP_LAYER_TILE_CODE_LO_WORD_POS)(DDP_LAYER_TILE_CODE_LO_HI_POS downto DDP_LAYER_TILE_CODE_LO_LO_POS));
        tile_info_v.code       := code_hi_v & code_lo_v;

        return tile_info_v;
    end extract_tile_info_from_layer_ram_line;

    function extract_global_layer_info_from_regs(layer_info_line_i : layer_info_line_t) return layer_info_t is
        variable layer_info_v : layer_info_t;
        constant layer_info_words_c : layer_info_words_t := layer_info_words_from_layer_info_line(layer_info_line_i);
    begin
        layer_info_v.priority           := unsigned(layer_info_words_c(DDP_LAYER_LAYER_PRIO_WORD_POS)(DDP_LAYER_LAYER_PRIO_HI_POS downto DDP_LAYER_LAYER_PRIO_LO_POS));
        layer_info_v.small_tile         := not layer_info_words_c(DDP_LAYER_TILE_SIZE_WORD_POS)(DDP_LAYER_TILE_SIZE_BIT_POS);
        layer_info_v.disabled           := layer_info_words_c(DDP_LAYER_LAYER_DISABLE_WORD_POS)(DDP_LAYER_LAYER_DISABLE_BIT_POS);
        layer_info_v.flip_x             := not layer_info_words_c(DDP_LAYER_FLIP_X_EN_WORD_POS)(DDP_LAYER_FLIP_X_EN_BIT_POS);
        layer_info_v.flip_y             := not layer_info_words_c(DDP_LAYER_FLIP_Y_EN_WORD_POS)(DDP_LAYER_FLIP_Y_EN_BIT_POS);
        layer_info_v.row_scroll_enabled := layer_info_words_c(DDP_LAYER_ROW_SCROLL_EN_WORD_POS)(DDP_LAYER_ROW_SCROLL_EN_BIT_POS);
        layer_info_v.row_select_enabled := layer_info_words_c(DDP_LAYER_ROW_SELECT_EN_WORD_POS)(DDP_LAYER_ROW_SELECT_EN_BIT_POS);
        layer_info_v.scroll_x           := unsigned(layer_info_words_c(DDP_LAYER_SCROLL_X_WORD_POS)(DDP_LAYER_SCROLL_X_HI_POS downto DDP_LAYER_SCROLL_X_LO_POS));
        layer_info_v.scroll_y           := unsigned(layer_info_words_c(DDP_LAYER_SCROLL_Y_WORD_POS)(DDP_LAYER_SCROLL_Y_HI_POS downto DDP_LAYER_SCROLL_Y_LO_POS));

        return layer_info_v;
    end extract_global_layer_info_from_regs;

    function palette_ram_addr_from_palette_color_select(palette_color_select_i : palette_color_select_t) return palette_ram_addr_t is
        variable palette_ram_addr_v : palette_ram_addr_t;
    begin

        palette_ram_addr_v := palette_color_select_i.palette & palette_color_select_i.color;

        return palette_ram_addr_v;
    end palette_ram_addr_from_palette_color_select;

end package body cave_pkg;
