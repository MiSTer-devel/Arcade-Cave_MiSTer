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
-- File         : main_ram.vhd
-- Description  : This is the main processor main ram
--
-- Author       : Rick Wertenbroek
-- Version      : 0.0
--
-- VHDL std     : 2008
-- Dependencies : simple_bram.vhd
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

entity main_ram is
    port (
        clk_i               : in  std_logic;
        enable_i            : in  std_logic;
        read_i              : in  std_logic;
        write_i             : in  std_logic;
        addr_i              : in  unsigned(15 downto 0);
        mask_i              : in  std_logic_vector(1 downto 0);
        data_i              : in  std_logic_vector(15 downto 0);
        data_o              : out std_logic_vector(15 downto 0);
        ack_o               : out std_logic
        );
end entity main_ram;

architecture struct of main_ram is

    signal data_s : std_logic_vector(data_o'length-1 downto 0);

begin

    assert data_i'length = data_o'length report "Data in and data out have different widths !" severity error;

    simple_bram_1 : entity work.single_port_ram
        generic map (
            ADDR_WIDTH => addr_i'length-1,
            DATA_WIDTH => data_i'length)
        port map (
            clk  => clk_i,
            cs   => enable_i,
            we   => write_i,
            addr => addr_i(addr_i'high downto 1),
            mask => mask_i,
            din  => data_i,
            dout => data_s);

    data_o <= data_s when enable_i = '1' else
              (others => '0');

    process(clk_i)
    begin
        if rising_edge(clk_i) then
            ack_o <= enable_i and (read_i or write_i);
        end if;
    end process;

end struct;
