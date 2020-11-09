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
-- File         : vctrl_regs.vhd
-- Description  :
--
-- Author       : Rick Wertenbroek
-- Version      : 0.0
--
-- VHDL std     : 2008
-- Dependencies :
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

entity vctrl_regs is
    port (
        -- 68k port
        clk_68k_i    : in  std_logic;
        enable_i     : in  std_logic;
        write_low_i  : in  std_logic;
        write_high_i : in  std_logic;
        read_low_i   : in  std_logic;
        read_high_i  : in  std_logic;
        addr_i       : in  unsigned(1 downto 0);
        data_i       : in  std_logic_vector(15 downto 0);
        data_o       : out std_logic_vector(15 downto 0);
        ack_o        : out std_logic;
        -- fast port (Read-Only Port)
        clk_fast_i   : in  std_logic;
        vctrl_o      : out std_logic_vector(3*16-1 downto 0)
        );
end entity vctrl_regs;

architecture struct of vctrl_regs is

    type regs_t is array (natural range <>) of std_logic_vector(15 downto 0);
    signal regs_s : regs_t(2 downto 0);

begin

    -- This is simply 3 16-bit regs
    register_process : process(clk_68k_i) is
    begin
        if rising_edge(clk_68k_i) then
            if enable_i = '1' then
                if write_low_i = '1' then
                    regs_s(to_integer(addr_i))(15 downto 8) <= data_i(15 downto 8);
                end if;

                if write_high_i = '1' then
                    regs_s(to_integer(addr_i))(7 downto 0) <= data_i(7 downto 0);
                end if;
            end if;
        end if;
    end process register_process;

    -- Reswap endianness
    data_o <= regs_s(to_integer(addr_i)) when enable_i = '1' else
              (others => '0');

    -- Ack
    process(clk_68k_i) is
    begin
        if rising_edge(clk_68k_i) then
            ack_o <= enable_i and (read_low_i or read_high_i or write_low_i or write_high_i);
        end if;
    end process;

    fast_register_process : process(clk_fast_i) is
    begin
        if rising_edge(clk_fast_i) then
            vctrl_o <= regs_s(2) & regs_s(1) & regs_s(0);
        end if;
    end process fast_register_process;

end struct;
