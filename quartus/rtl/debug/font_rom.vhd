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
-- File         : font_rom.vhd
-- Description  : A simple font rom with the characters 0-9 A-F
--
-- Author       : Rick Wertenbroek
-- Version      : 1.0
--
-- Dependencies : -
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

entity font_rom is
    port(
        clk_i        : in  std_logic;
        hex_number_i : in  std_logic_vector(3 downto 0);
        row_i        : in  std_logic_vector(3 downto 0);
        font_row_o   : out std_logic_vector(7 downto 0)
        );
end font_rom;

architecture rom of font_rom is

    -- Characters are 8 pix wide and 10 pix high and there are 16 hex numbers
    type rom_t is array (0 to 10*16-1) of std_logic_vector(7 downto 0);
    signal reverse_s : std_logic_vector(7 downto 0);

    signal rom_s : rom_t := (
        -- 0
        "01111100", -- 0
        "11000110", -- 1
        "11000110", -- 2
        "11001110", -- 3
        "11011110", -- 4
        "11110110", -- 5
        "11100110", -- 6
        "11000110", -- 7
        "11000110", -- 8
        "01111100", -- 9
        -- 1
        "00011000", -- 0
        "00111000", -- 1
        "01111000", -- 2
        "00011000", -- 3
        "00011000", -- 4
        "00011000", -- 5
        "00011000", -- 6
        "00011000", -- 7
        "00011000", -- 8
        "01111110", -- 9
        -- 2
        "01111100", -- 0
        "11000110", -- 1
        "00000110", -- 2
        "00001100", -- 3
        "00011000", -- 4
        "00110000", -- 5
        "01100000", -- 6
        "11000000", -- 7
        "11000110", -- 8
        "11111110", -- 9
        -- 3
        "01111100", -- 0
        "11000110", -- 1
        "00000110", -- 2
        "00000110", -- 3
        "00111100", -- 4
        "00000110", -- 5
        "00000110", -- 6
        "00000110", -- 7
        "11000110", -- 8
        "01111100", -- 9
        -- 4
        "00001100", -- 0
        "00011100", -- 1
        "00111100", -- 2
        "01101100", -- 3
        "11001100", -- 4
        "11111110", -- 5
        "00001100", -- 6
        "00001100", -- 7
        "00001100", -- 8
        "00011110", -- 9
        -- 5
        "11111110", -- 0
        "11000000", -- 1
        "11000000", -- 2
        "11000000", -- 3
        "11111100", -- 4
        "00000110", -- 5
        "00000110", -- 6
        "00000110", -- 7
        "11000110", -- 8
        "01111100", -- 9
        -- 6
        "00111000", -- 0
        "01100000", -- 1
        "11000000", -- 2
        "11000000", -- 3
        "11111100", -- 4
        "11000110", -- 5
        "11000110", -- 6
        "11000110", -- 7
        "11000110", -- 8
        "01111100", -- 9
        -- 7
        "11111110", -- 0
        "11000110", -- 1
        "00000110", -- 2
        "00000110", -- 3
        "00001100", -- 4
        "00011000", -- 5
        "00110000", -- 6
        "00110000", -- 7
        "00110000", -- 8
        "00110000", -- 9
        -- 8
        "01111100", -- 0
        "11000110", -- 1
        "11000110", -- 2
        "11000110", -- 3
        "01111100", -- 4
        "11000110", -- 5
        "11000110", -- 6
        "11000110", -- 7
        "11000110", -- 8
        "01111100", -- 9
        -- 9
        "01111100", -- 0
        "11000110", -- 1
        "11000110", -- 2
        "11000110", -- 3
        "01111110", -- 4
        "00000110", -- 5
        "00000110", -- 6
        "00000110", -- 7
        "00001100", -- 8
        "01111000", -- 9
        -- A
        "00010000", -- 0
        "00111000", -- 1
        "01101100", -- 2
        "11000110", -- 3
        "11000110", -- 4
        "11111110", -- 5
        "11000110", -- 6
        "11000110", -- 7
        "11000110", -- 8
        "11000110", -- 9
        -- B
        "11111100", -- 0
        "01100110", -- 1
        "01100110", -- 2
        "01100110", -- 3
        "01111100", -- 4
        "01100110", -- 5
        "01100110", -- 6
        "01100110", -- 7
        "01100110", -- 8
        "11111100", -- 9
        -- C
        "00111100", -- 0
        "01100110", -- 1
        "11000010", -- 2
        "11000000", -- 3
        "11000000", -- 4
        "11000000", -- 5
        "11000000", -- 6
        "11000010", -- 7
        "01100110", -- 8
        "00111100", -- 9
        -- D
        "11111000", -- 0
        "01101100", -- 1
        "01100110", -- 2
        "01100110", -- 3
        "01100110", -- 4
        "01100110", -- 5
        "01100110", -- 6
        "01100110", -- 7
        "01101100", -- 8
        "11111000", -- 9
        -- E
        "11111110", -- 0
        "01100110", -- 1
        "01100010", -- 2
        "01101000", -- 3
        "01111000", -- 4
        "01101000", -- 5
        "01100000", -- 6
        "01100010", -- 7
        "01100110", -- 8
        "11111110", -- 9
        -- F
        "11111110", -- 0
        "01100110", -- 1
        "01100010", -- 2
        "01101000", -- 3
        "01111000", -- 4
        "01101000", -- 5
        "01100000", -- 6
        "01100000", -- 7
        "01100000", -- 8
        "11110000"  -- 9
        );
begin

    rom_process : process (clk_i) is
        variable index_v : integer;
    begin
        if rising_edge(clk_i) then
            index_v := 10*to_integer(unsigned(hex_number_i)) + to_integer(unsigned(row_i));
            reverse_s <= rom_s(index_v);
        end if;
    end process;

    -- Reverse the bits
    process (reverse_s) is
    begin
        for i in font_row_o'range loop
            font_row_o(i) <= reverse_s(reverse_s'high-i);
        end loop;
    end process;

end rom;
