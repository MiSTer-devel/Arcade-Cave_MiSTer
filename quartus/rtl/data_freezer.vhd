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
-- File         : data_freezer.vhd
-- Description  : A clock domain crossing component, that takes data from a
--                slow clock domain, transfers it to the fast clock domain and
--                receives data back from the fast clock domain. Data which is
--                then frozen for the slow clock domain to sample until new
--                data arrives from the slow clock domain.
--
--                This is used to issue memory requests from a slow clock
--                domain to a memory in the fast clock domain, since the
--                requests are not pipelined or bursted we can simply freeze
--                the data between requests. Otherwise a dual clock FIFO should
--                be used.
--
--                This was made in a project where a processor in a slow clock
--                domain would issue read commands on a memory bus, and this
--                processor would not do anything else before the data came
--                back (and there was no DMA).
--
-- Author       : Rick Wertenbroek
-- Version      : 0.0
--
-- VHDL std     : 2008
-- Dependencies : -
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

entity data_freezer is
    generic (
        WIDTH_A : natural := 8;
        WIDTH_B : natural := 8
    );
    port (
        -- Clocks and Resets
        slow_clk_i : in  std_logic;
        slow_rst_i : in  std_logic;
        fast_clk_i : in  std_logic;
        fast_rst_i : in  std_logic;
        -- Slow Clock interface
        data_sc_i  : in  std_logic_vector(WIDTH_A-1 downto 0);
        write_sc_i : in  std_logic;
        data_sc_o  : out std_logic_vector(WIDTH_B-1 downto 0);
        valid_sc_o : out std_logic;
        -- Fast Clock interface
        data_fc_i  : in  std_logic_vector(WIDTH_B-1 downto 0);
        write_fc_i : in  std_logic;
        data_fc_o  : out std_logic_vector(WIDTH_A-1 downto 0);
        valid_fc_o : out std_logic
        );
end entity data_freezer;

architecture rtl of data_freezer is

    -------------
    -- Signals --
    -------------

    -- xcd means Cross Clock Domain
    -- The default values are just for simulation purposes, they could be any
    -- value in FPGA (even random uninitialized values in the registers).
    signal data_sc_fc_reg_xcd_s   : std_logic_vector(data_sc_i'length-1 downto 0) := (others => '0');
    signal data_sc_fc_reg_s       : std_logic_vector(data_fc_o'length-1 downto 0) := (others => '0');
    signal write_sc_fc_reg_xcd_s  : std_logic;
    signal written_sc_fc_meta_s   : std_logic; -- Could go metastable, only one
                                               -- wire should go out of this
                                               -- register and that is to
                                               -- another register
    signal written_sc_fc_reg_1_s  : std_logic;
    signal written_sc_fc_reg_2_s  : std_logic;
    signal data_arrived_from_sc_s : std_logic;
    signal valid_fc_data_o_reg_s  : std_logic;

    signal data_fc_sc_reg_xcd_s   : std_logic_vector(data_fc_i'length-1 downto 0) := (others => '0');
    signal data_fc_sc_reg_s       : std_logic_vector(data_sc_o'length-1 downto 0) := (others => '0');
    signal freeze_fc_sc_reg_xcd_s : std_logic;
    signal frozen_fc_sc_meta_s    : std_logic; -- Could go metastable, only one
                                               -- wire should go out of this
                                               -- register and that is to
                                               -- another register
    signal frozen_fc_sc_reg_1_s   : std_logic;
    signal frozen_fc_sc_reg_2_s   : std_logic;
    signal data_arrived_from_fc_s : std_logic;
    signal unfreeze_reg_s         : std_logic;
    signal valid_sc_data_o_reg_s  : std_logic;

begin

    assert data_sc_i'length = data_fc_o'length report "Data bus sizes do not match !" severity error;
    assert data_sc_o'length = data_fc_i'length report "Data bus sizes do not match !" severity error;

    --------------------------------
    -- Slow to Fast Data Transfer --
    --------------------------------

    -- Slow Clock Domain
    --------------------
    process(slow_clk_i)
    begin
        if rising_edge(slow_clk_i) then
            -- Flag register
            if slow_rst_i = '1' then
                write_sc_fc_reg_xcd_s <= '0';
            else
                write_sc_fc_reg_xcd_s <= write_sc_i;
            end if;

            -- Data register
            if write_sc_i = '1' then
                data_sc_fc_reg_xcd_s <= data_sc_i;
            end if;
        end if;
    end process;

    -- Fast Clock Domain
    --------------------

    -- Edge detection, the slow clock is assumed much slower than the fast
    -- clock so there is no hold (freeze) on the write_sc_i signal. Therefore a
    -- single slow clock cycle assertion of write_sc_i seems already frozen to
    -- the fast clock.
    data_arrived_from_sc_s <= written_sc_fc_reg_1_s and not written_sc_fc_reg_2_s;

    process(fast_clk_i)
    begin
        if rising_edge(fast_clk_i) then
            -- Flag registers
            if fast_rst_i = '1' then
                written_sc_fc_meta_s  <= '0';
                written_sc_fc_reg_1_s <= '0';
                written_sc_fc_reg_2_s <= '0';
                valid_fc_data_o_reg_s <= '0';
            else
                written_sc_fc_meta_s  <= write_sc_fc_reg_xcd_s;
                written_sc_fc_reg_1_s <= written_sc_fc_meta_s;
                written_sc_fc_reg_2_s <= written_sc_fc_reg_1_s;
                valid_fc_data_o_reg_s <= data_arrived_from_sc_s;
            end if;

            -- Data register
            if data_arrived_from_sc_s = '1' then
                -- Sample the slow clock domain data
                data_sc_fc_reg_s      <= data_sc_fc_reg_xcd_s;
            end if;
        end if;
    end process;

    -- Outputs
    data_fc_o  <= data_sc_fc_reg_s;
    valid_fc_o <= valid_fc_data_o_reg_s;

    --------------------------------
    -- Fast to Slow Data Transfer --
    --------------------------------

    -- Fast Clock Domain
    --------------------
    process(fast_clk_i)
    begin
        if rising_edge(fast_clk_i) then
            -- Flag register
            if fast_rst_i = '1' then
                freeze_fc_sc_reg_xcd_s <= '0';
                unfreeze_reg_s         <= '0';
            else
                if write_fc_i = '1' then
                    freeze_fc_sc_reg_xcd_s <= '1'; -- Set
                elsif unfreeze_reg_s = '1' then
                    -- The data can be unfrozen when the slow clock domain has
                    -- received it.
                    freeze_fc_sc_reg_xcd_s <= '0'; -- Unset
                end if;
                -- Unfreeze, this also crosses the clock domain
                unfreeze_reg_s <= valid_sc_data_o_reg_s;
            end if;

            -- Data register
            -- The Fast Clock domain should not write when already frozen,
            -- since this will change the data that is supposed to be frozen.
            -- However since this crossing is a Request-Response, there should
            -- be no new data before the slow clock issues a new request. One
            -- could add a test condition like "and freeze_fc_sc_reg_xcd_s =
            -- '0'" and output a busy signal when frozen if needed but the fast
            -- clock should not issue data before a new request...
            if write_fc_i = '1' then
                data_fc_sc_reg_xcd_s <= data_fc_i;
            end if;
        end if;
    end process;

    -- Edge detection on the frozen signal
    data_arrived_from_fc_s <= frozen_fc_sc_reg_1_s and not frozen_fc_sc_reg_2_s;

    process(slow_clk_i)
    begin
        if rising_edge(slow_clk_i) then
            -- Flag registers
            if slow_rst_i = '1' then
                frozen_fc_sc_meta_s   <= '0';
                frozen_fc_sc_reg_1_s  <= '0';
                frozen_fc_sc_reg_2_s  <= '0';
                valid_sc_data_o_reg_s <= '0';
            else
                frozen_fc_sc_meta_s   <= freeze_fc_sc_reg_xcd_s;
                frozen_fc_sc_reg_1_s  <= frozen_fc_sc_meta_s;
                frozen_fc_sc_reg_2_s  <= frozen_fc_sc_reg_1_s;
                valid_sc_data_o_reg_s <= data_arrived_from_fc_s;
            end if;

            -- Data register
            if data_arrived_from_fc_s = '1' then
                -- Sample the fast clock domain data
                data_fc_sc_reg_s      <= data_fc_sc_reg_xcd_s;
            end if;
        end if;
    end process;

    -- Outputs
    data_sc_o  <= data_fc_sc_reg_s;
    valid_sc_o <= valid_sc_data_o_reg_s;

end rtl;
