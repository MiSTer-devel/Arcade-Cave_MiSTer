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
-- File         : fb_from_ddr3_dma.vhd
-- Description  : Launches a transfer from DDR3 when the receiving FIFO has
--                enough space (at least 128 64-bit words).
--
-- Author       : Rick Wertenbroek
-- Date         : 01.01.20
-- Version      : 0.0
--
-- VHDL std     : 2008
-- Dependencies : log_pkg.vhd
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;
use work.log_pkg.all;

entity fb_from_ddr3_dma is
    port(
        clk_i              : in  std_logic;
        rst_i              : in  std_logic;
        --
        address_i          : in  unsigned(31 downto 0);
        hi_nlo_i           : in  std_logic;
        --
        fifo_ready_i       : in  std_logic;
        data_o             : out std_logic_vector(63 downto 0);
        write_fifo_o       : out std_logic;
        --
        addr_ddr3_o        : out unsigned(31 downto 0);
        burstcount_ddr3_o  : out std_logic_vector(7 downto 0);
        waitrequest_ddr3_i : in  std_logic;
        read_ddr3_o        : out std_logic;
        data_ddr3_i        : in  std_logic_vector(63 downto 0);
        data_valid_ddr3_i  : in  std_logic);
end fb_from_ddr3_dma;

architecture rtl of fb_from_ddr3_dma is
    ---------------
    -- Constants --
    ---------------
    constant WORDS_TO_TRANSFER_C : natural := (320*240*16)/64;
    constant BURST_LENGTH_C      : natural := 128;
    constant NUMBER_OF_BURSTS_C  : natural := WORDS_TO_TRANSFER_C / BURST_LENGTH_C;

    -------------
    -- Signals --
    -------------
    signal read_ddr3_s          : std_logic;
    signal offset_s             : unsigned(31 downto 0);
    signal address_s            : unsigned(31 downto 0);
    --
    signal word_counter_s       : unsigned(7 downto 0);
    signal word_counter_next_s  : unsigned(7 downto 0);
    signal reset_word_counter_s : std_logic;
    signal burst_counter_s      : unsigned(ilogup(NUMBER_OF_BURSTS_C)-1 downto 0);
    --
    signal not_ready_to_burst_s     : std_logic;

begin

    assert (BURST_LENGTH_C = 128) report "Do not change constants !" severity error;

    --------------
    -- Counters --
    --------------
    word_counter_next_s <= word_counter_s + 1 when data_valid_ddr3_i = '1' else word_counter_s;
    reset_word_counter_s <= '1' when (word_counter_next_s = BURST_LENGTH_C) or (rst_i = '1') else '0';

    word_counter_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if reset_word_counter_s = '1' then
                word_counter_s <= (others => '0');
            else
                word_counter_s <= word_counter_next_s;
            end if;
        end if;
    end process word_counter_process;

    burst_counter_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if rst_i = '1' then
                burst_counter_s <= (others => '0');
            else
                if word_counter_next_s = BURST_LENGTH_C then
                    if burst_counter_s = NUMBER_OF_BURSTS_C-1 then
                        burst_counter_s <= (others => '0');
                    else
                        burst_counter_s <= burst_counter_s + 1;
                    end if;
                end if;
            end if;
        end if;
    end process burst_counter_process;

    ready_to_burst_register_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if rst_i = '1' then
                not_ready_to_burst_s <= '0';
            else
                if (read_ddr3_s = '1') and (waitrequest_ddr3_i = '0') then
                    not_ready_to_burst_s <= '1'; -- At the start of a burst read
                elsif word_counter_next_s = BURST_LENGTH_C then
                    not_ready_to_burst_s <= '0'; -- At the end of a burst read
                end if;
            end if;
        end if;
    end process ready_to_burst_register_process;

    -- Launch a burst when we are ready
    read_ddr3_s <= (not not_ready_to_burst_s) and fifo_ready_i;

    offset_s  <= resize(hi_nlo_i & burst_counter_s & to_unsigned(0, ilogup(BURST_LENGTH_C*8)), offset_s'length);
    address_s <= address_i + offset_s;

    -------------
    -- Outputs --
    -------------

    -- To DDR3
    burstcount_ddr3_o <= std_logic_vector(to_unsigned(BURST_LENGTH_C, burstcount_ddr3_o'length));
    read_ddr3_o       <= read_ddr3_s;
    addr_ddr3_o       <= address_s;

    -- To FIFO
    data_o       <= data_ddr3_i;
    write_fifo_o <= data_valid_ddr3_i;

end architecture rtl;
