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
-- File         : debug_display.vhd
-- Description  : This debug display shows a 32-bit number as 8 hex chars.
--
-- Author       : Rick Wertenbroek
-- Version      : 1.0
--
-- Dependencies : font_rom.vhd, hdmi_pkg.vhd
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

entity debug_display is
    port (
        clk_i   : in  std_logic;
        value_i : in  std_logic_vector(31 downto 0);
        x_i     : in  std_logic_vector(5 downto 0);
        y_i     : in  std_logic_vector(3 downto 0);
        pixel_o : out std_logic_vector(23 downto 0)
        );
end debug_display;

architecture rtl of debug_display is

    signal hex_number_s : std_logic_vector(3 downto 0);
    signal x_number_s   : unsigned(2 downto 0);
    signal font_row_s   : std_logic_vector(7 downto 0);
    signal in_row_sel_s : unsigned(2 downto 0);

    signal value_s      : std_logic_vector(value_i'length-1 downto 0);

begin

    -- Input register
    process(clk_i) is
    begin
        if rising_edge(clk_i) then
            value_s <= value_i;
        end if;
    end process;

    -- The number is selected by the 3 MSB bits of x (since the 3 lower bits
    -- are for the pixel of the number and each number is 8 pixels wide).
    x_number_s <= 7 - unsigned(x_i(5 downto 3));

    -- Select the number to be shown of the 32 bit vector
    hex_number_s <= value_s((to_integer(x_number_s)+1)*4-1 downto to_integer(x_number_s)*4);

    -- Font ROM
    font_rom_1 : entity work.font_rom
        port map (
            clk_i        => clk_i,
            hex_number_i => hex_number_s,
            row_i        => y_i,
            font_row_o   => font_row_s);

    -- Since the font row is delayed by one clock cycle we need to delay the
    -- selection of the pixel in the row also by one cycle.
    process (clk_i) is
    begin
        if rising_edge(clk_i) then
            in_row_sel_s <= unsigned(x_i(2 downto 0));
        end if;
    end process;

    -- White when 1 else Black
    pixel_o <= (others => '1') when font_row_s(to_integer(in_row_sel_s)) = '1' else
               (others => '0');

end rtl;
