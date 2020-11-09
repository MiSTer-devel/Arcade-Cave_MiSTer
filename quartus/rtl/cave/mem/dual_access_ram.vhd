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
-- File         : dual_access_ram.vhd
-- Description  :
--
-- Author       : Rick Wertenbroek
-- Version      : 0.1
--
-- VHDL std     : 2008
-- Dependencies :
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

entity dual_access_ram is
    port (
        -- 68k port
        clk_68k_i    : in  std_logic;
        enable_i     : in  std_logic;
        write_low_i  : in  std_logic;
        write_high_i : in  std_logic;
        read_low_i   : in  std_logic;
        read_high_i  : in  std_logic;
        addr_i       : in  unsigned;
        data_i       : in  std_logic_vector(15 downto 0);
        data_o       : out std_logic_vector(15 downto 0);
        ack_o        : out std_logic;
        -- fast port (Read-Only Port)
        clk_fast_i   : in  std_logic;
        addr_fast_i  : in  unsigned;
        data_fast_o  : out std_logic_vector
    );
end entity dual_access_ram;

architecture struct of dual_access_ram is

    signal data_s           : std_logic_vector(data_o'length-1 downto 0);
    signal data_fast_low_s  : std_logic_vector(data_fast_o'length/2-1 downto 0);
    signal data_fast_high_s : std_logic_vector(data_fast_o'length/2-1 downto 0);

begin

    true_dual_port_bram_1 : entity work.true_dual_port_ram
        generic map (
            DATA_WIDTH_A => data_i'length/2,
            ADDR_WIDTH_A => addr_i'length-1,
            DATA_WIDTH_B => data_fast_o'length/2,
            ADDR_WIDTH_B => addr_fast_i'length)
        port map (
            clk_a  => clk_68k_i,
            cs_a   => enable_i,
            wr_a   => write_low_i,
            addr_a => addr_i(addr_i'high downto 1),
            din_a  => data_i(15 downto 8), -- Big Endian
            dout_a => data_s(15 downto 8),
            clk_b  => clk_fast_i,
            addr_b => addr_fast_i(addr_fast_i'high downto 0),
            dout_b => data_fast_high_s); -- Little Endian

    true_dual_port_bram_2 : entity work.true_dual_port_ram
        generic map (
            DATA_WIDTH_A => data_i'length/2,
            ADDR_WIDTH_A => addr_i'length-1,
            DATA_WIDTH_B => data_fast_o'length/2,
            ADDR_WIDTH_B => addr_fast_i'length)
        port map (
            clk_a  => clk_68k_i,
            cs_a   => enable_i,
            wr_a   => write_high_i,
            addr_a => addr_i(addr_i'high downto 1),
            din_a  => data_i(7 downto 0), -- Big Endian
            dout_a => data_s(7 downto 0),
            clk_b  => clk_fast_i,
            addr_b => addr_fast_i(addr_fast_i'high downto 0),
            dout_b => data_fast_low_s); -- Little Endian

    data_o <= data_s when enable_i = '1' else
              (others => '0');

    process(all) is
    begin
        for i in 0 to data_fast_o'length/8/2-1 loop
            data_fast_o((i*2)*8+8-1 downto (i*2)*8)     <= data_fast_low_s(i*8+8-1 downto i*8);
            data_fast_o((i*2+1)*8+8-1 downto (i*2+1)*8) <= data_fast_high_s(i*8+8-1 downto i*8);
        end loop;
    end process;

    process(clk_68k_i) is
    begin
        if rising_edge(clk_68k_i) then
            ack_o <= enable_i and (read_low_i or read_high_i or write_low_i or write_high_i);
        end if;
    end process;

end struct;
