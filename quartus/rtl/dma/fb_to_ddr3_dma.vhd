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
-- File         : fb_to_ddr3_dma.vhd
-- Description  : DMA to transfer frame buffer contents to DDR3
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

entity fb_to_ddr3_dma is
    generic (
        DDR3_ADDRESS_WIDTH_G : natural := 32);
    port (
        clk_i              : in  std_logic;
        rst_i              : in  std_logic;
        -- Signals to DMA
        address_i          : in  unsigned(31 downto 0);
        transfer_i         : in  std_logic;
        hi_nlo_i           : in  std_logic;
        busy_o             : out std_logic;
        done_o             : out std_logic;
        -- Signals to Frame Buffer
        fb_addr_o          : out unsigned(14 downto 0);
        fb_data_i          : in  std_logic_vector(63 downto 0);
        -- Signals to DDR3
        address_ddr3_o     : out unsigned(DDR3_ADDRESS_WIDTH_G-1 downto 0);
        burstcount_ddr3_o  : out std_logic_vector(7 downto 0);
        write_ddr3_o       : out std_logic;
        writedata_ddr3_o   : out std_logic_vector(63 downto 0);
        waitrequest_ddr3_i : in  std_logic);
end entity fb_to_ddr3_dma;

architecture rtl of fb_to_ddr3_dma is

    ---------------
    -- Constants --
    ---------------
    constant WORDS_TO_TRANSFER_C : natural := (320*240*16)/64;
    constant BURST_LENGTH_C      : natural := 128;
    constant NUMBER_OF_BURSTS_C  : natural := WORDS_TO_TRANSFER_C / BURST_LENGTH_C;

    -----------
    -- Types --
    -----------
    type state_t is (IDLE, TRANSFER);

    -------------
    -- Signals --
    -------------
    signal state_reg_s:  state_t;
    signal state_next_s: state_t;

    signal effective_write_s: std_logic;
    signal transfer_done_s:   std_logic;

    signal word_counter_s:            unsigned(7 downto 0);
    signal word_counter_next_s:       unsigned(7 downto 0);
    signal reset_word_counter_s:      std_logic;
    signal total_word_counter_s:      unsigned(ilogup(WORDS_TO_TRANSFER_C)-1 downto 0);
    signal total_word_counter_next_s: unsigned(ilogup(WORDS_TO_TRANSFER_C)-1 downto 0);
    signal burst_counter_s:           unsigned(ilogup(NUMBER_OF_BURSTS_C)-1 downto 0);

    signal offset_s:          unsigned(31 downto 0);
    signal address_ddr3_s:    unsigned(DDR3_ADDRESS_WIDTH_G-1 downto 0);
    signal burstcount_ddr3_s: std_logic_vector(7 downto 0);
    signal write_ddr3_s:      std_logic;
    signal writedata_ddr3_s:  std_logic_vector(63 downto 0);
begin

    assert (BURST_LENGTH_C = 128) report "Do not change constants !" severity error;

    ---------
    -- FSM --
    ---------
    fsm_state_reg_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if rst_i = '1' then
                state_reg_s <= IDLE;
            else
                state_reg_s <= state_next_s;
            end if;
        end if;
    end process fsm_state_reg_process;

    fsm_next_state_process : process(all) is
    begin
        state_next_s <= state_reg_s; -- Default

        case state_reg_s is
            when IDLE =>
                if transfer_i = '1' then
                    state_next_s <= TRANSFER;
                end if;

            when TRANSFER =>
                if transfer_done_s = '1' then
                    state_next_s <= IDLE;
                end if;
        end case;
    end process fsm_next_state_process;

    --------------
    -- Counters --
    --------------

    effective_write_s <= '1' when (waitrequest_ddr3_i = '0') and (write_ddr3_s = '1') else '0';
    word_counter_next_s <= word_counter_s + 1 when effective_write_s = '1' else word_counter_s;
    total_word_counter_next_s <= total_word_counter_s + 1 when state_reg_s = TRANSFER else total_word_counter_s;
    reset_word_counter_s <= '1' when (word_counter_next_s = BURST_LENGTH_C) or state_reg_s = IDLE else '0';

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
            if state_reg_s = IDLE then
                burst_counter_s <= (others => '0');
            else
                if (effective_write_s = '1') and (word_counter_next_s = BURST_LENGTH_C) then
                    burst_counter_s <= burst_counter_s + 1;
                end if;
            end if;
        end if;
    end process burst_counter_process;

    total_word_counter_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if state_next_s = IDLE then
                total_word_counter_s <= (others => '0');
            else
                if effective_write_s = '1' then
                    total_word_counter_s <= total_word_counter_next_s;
                end if;
            end if;
        end if;
    end process total_word_counter_process;

    transfer_done_s   <= '1' when (word_counter_next_s = BURST_LENGTH_C) and (burst_counter_s = NUMBER_OF_BURSTS_C-1) else '0';
    offset_s          <= resize(hi_nlo_i & burst_counter_s & to_unsigned(0, ilogup(BURST_LENGTH_C*8)), offset_s'length);
    address_ddr3_s    <= address_i + offset_s;
    burstcount_ddr3_s <= std_logic_vector(to_unsigned(BURST_LENGTH_C, burstcount_ddr3_s'length));
    write_ddr3_s      <= '1' when state_reg_s = TRANSFER else '0';
    writedata_ddr3_s  <= fb_data_i;

    -------------
    -- Outputs --
    -------------
    busy_o            <= '0' when state_reg_s = IDLE else '1';
    done_o            <= transfer_done_s;
    fb_addr_o         <= total_word_counter_next_s when effective_write_s = '1' else total_word_counter_s;
    address_ddr3_o    <= address_ddr3_s;
    burstcount_ddr3_o <= burstcount_ddr3_s;
    write_ddr3_o      <= write_ddr3_s;
    writedata_ddr3_o  <= writedata_ddr3_s;

end architecture rtl;
