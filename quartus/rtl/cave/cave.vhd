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
-- File         : cave.vhd
-- Description  : Dodonpachi - contains :
--                The Main Processor a motorola 68000
--                The RAMs
--                The Graphic Hardware (sprites and layers)
--
-- Author       : Rick Wertenbroek
-- Version      : 0.2
--
-- Dependencies : All of its contents
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use work.cave_pkg.all;

entity cave is
    generic (
        INCLUDE_GRAPHIC_PROCESSOR_G : boolean := true;
        INCLUDE_EEPROM_G            : boolean := false);
    port (
        -- Fast clock domain
        rst_i                    : in  std_logic;
        clk_i                    : in  std_logic;
        -- CPU clock domain
        rst_68k_i                : in  std_logic;
        clk_68k_i                : in  std_logic;
        -- Vertical blank signal
        vblank_i                 : in std_logic;
        -- CPU
        cpu_cen                  : out std_logic;
        cpu_addr                 : in  unsigned(31 downto 0);
        cpu_din                  : out std_logic_vector(15 downto 0);
        cpu_dout                 : in  std_logic_vector(15 downto 0);
        cpu_as                   : in  std_logic;
        cpu_rw                   : in  std_logic;
        cpu_uds                  : in  std_logic;
        cpu_lds                  : in  std_logic;
        cpu_dtack                : out std_logic;
        cpu_ipl                  : out std_logic_vector(2 downto 0);
        cpu_debug_pc             : in std_logic_vector(31 downto 0);
        cpu_debug_pcw            : in std_logic;
        -- Memory bus
        memBus_ack               : out std_logic;
        memBus_data              : out std_logic_vector(15 downto 0)
        );
end entity cave;

architecture struct of cave is

    -- Processor related signals
    signal rst_68k_s                       : std_logic;
    signal addr_68k_s                      : unsigned(31 downto 0);  -- only 24 MSB bits used
    signal data_out_68k_s                  : word_t;
    signal addr_strobe_68k_s               : std_logic;
    signal addr_strobe_68k_old_reg_s       : std_logic;
    signal upper_data_select_68k_s         : std_logic;
    signal upper_data_select_68k_old_reg_s : std_logic;
    signal lower_data_select_68k_s         : std_logic;
    signal lower_data_select_68k_old_reg_s : std_logic;
    signal read_n_write_68k_s              : std_logic;
    signal ipl_s                           : std_logic_vector(2 downto 0);
    signal other_ack_s                     : std_logic;
    signal other_data_o_s                  : word_t;

    -- Memory bus related signals
    signal memory_bus_ack_s         : std_logic;
    signal memory_bus_data_s        : word_t;
    signal read_strobe_s            : std_logic;
    signal write_strobe_s           : std_logic;
    signal high_write_strobe_s      : std_logic;
    signal low_write_strobe_s       : std_logic;
    -- Edge Cases
    signal edge_case_enable_s       : std_logic;
    signal edge_case_ack_s          : std_logic;
    -- No data for edge cases (will be 0 since the bus is OR'ed)

    signal frame_buffer_color_s     : color_t;

begin

    -----------------------
    -- IO with top level --
    -----------------------

    -- Sync
    process(clk_68k_i) is
    begin
        if rising_edge(clk_68k_i) then
            rst_68k_s <= rst_68k_i;
        end if;
    end process;

    -------------------
    -- Interruptions --
    -------------------
    interrupt_block : block
        signal sync_reg_s   : std_logic_vector(3 downto 0);
        signal vblank_irq_s : std_logic;
    begin

        -- Synchronisation shift register
        sync_process : process(clk_68k_i) is
        begin
            if rising_edge(clk_68k_i) then
                sync_reg_s(0) <= vblank_i;
                for i in 1 to sync_reg_s'high loop
                    sync_reg_s(i) <= sync_reg_s(i-1);
                end loop;
            end if;
        end process sync_process;

        -- Detect a rising edge on the VBLANK signal
        vblank_irq_s <= (not sync_reg_s(sync_reg_s'high)) and sync_reg_s(sync_reg_s'high-1);

        interrupt_process : process(clk_68k_i) is
        begin
            if rising_edge(clk_68k_i) then
                if rst_68k_i = '1' then
                    ipl_s <= (others => '0');
                else
                    -- This only simulates the V-Blank IRQ for now (TODO : Other IRQs and ACKs)
                    if vblank_irq_s = '1' then
                        ipl_s <= "001";
                    else
                        if (read_strobe_s = '1') and (addr_68k_s(23 downto 0) = x"800004") then
                            -- The 68k acknowledged the IRQ (I am not sure about this...)
                            ipl_s <= (others => '0');
                        end if; -- IRQ Ack
                    end if; -- IRQ fired
                end if; -- Reset
            end if; -- Rising Edge Clock
        end process interrupt_process;

        -- Test for interrupts
        process(clk_68k_i) is
        begin
            if rising_edge(clk_68k_i) then
                other_ack_s    <= '0';
                other_data_o_s <= (others => '0');
                if (read_strobe_s = '1') and (addr_68k_s(addr_68k_s'high downto addr_68k_s'high-3) = x"F") then
                    other_ack_s    <= '1';
                    --other_data_o_s <= x"0019"; -- The 68k uses auto-vectorized interrupts
                end if;
            end if;
        end process;

    end block interrupt_block;

    --------------------
    -- Main Processor --
    --------------------

    cpu_cen <= '1';
    addr_68k_s <= cpu_addr;
    cpu_din <= memory_bus_data_s;
    data_out_68k_s <= cpu_dout;
    addr_strobe_68k_s <= cpu_as;
    read_n_write_68k_s <= cpu_rw;
    upper_data_select_68k_s <= cpu_uds;
    lower_data_select_68k_s <= cpu_lds;
    cpu_dtack <= memory_bus_ack_s;
    cpu_ipl <= ipl_s;

    ---------------------
    -- Main Memory Bus --
    ---------------------

    -- This is an OR'ed bus
    memory_bus_ack_s <= edge_case_ack_s   or
                        other_ack_s;

    memBus_ack <= memory_bus_ack_s;

    -- "OR" everything together to create the "OR'ed" bus
    memory_bus_data_s <= other_data_o_s;

    memBus_data <= memory_bus_data_s;

    -- We register the address strobe in order to detect when it is asserted in
    -- order to make a single clock read/write strobe below
    addr_strobe_reg_process : process(clk_68k_i) is
    begin
        if rising_edge(clk_68k_i) then
            if rst_68k_s = '1' then
                addr_strobe_68k_old_reg_s       <= '0';
                upper_data_select_68k_old_reg_s <= '0';
                lower_data_select_68k_old_reg_s <= '0';
            else
                addr_strobe_68k_old_reg_s       <= addr_strobe_68k_s;
                upper_data_select_68k_old_reg_s <= upper_data_select_68k_s;
                lower_data_select_68k_old_reg_s <= lower_data_select_68k_s;
            end if;
        end if;
    end process addr_strobe_reg_process;

    -- These strobes indicate a read or write operation from the processor
    read_strobe_s       <= addr_strobe_68k_s and (not addr_strobe_68k_old_reg_s) and read_n_write_68k_s;
    write_strobe_s      <= addr_strobe_68k_s and (not addr_strobe_68k_old_reg_s) and (not read_n_write_68k_s);
    high_write_strobe_s <= upper_data_select_68k_s and (not upper_data_select_68k_old_reg_s) and (not read_n_write_68k_s);
    low_write_strobe_s  <= lower_data_select_68k_s and (not lower_data_select_68k_old_reg_s) and (not read_n_write_68k_s);

    -- Memory bus decode logic - Dodonpachi Address Map -- TODO Change to constants
    ---------------------------------------------------

    -- Edge Cases
    edge_case_enable_s   <= '1' when (addr_68k_s(23 downto 16) = x"5f") or
                                     (addr_68k_s(31 downto 24) /= x"00") else
                            '0';
    -- Access to 0x5fxxxx appears in dodonpachi on attract loop when showing
    -- the air stage on frame 9355 i.e., after roughly 2 min 30 sec
    -- The game is accessing data relative to a Layer 1 address and underflows,
    -- these accesses do nothing but should be acknowledged in order not to
    -- block de CPU.
    -- The reason these accesses appear is probably because it made the layer
    -- update routine simpler to write (no need to handle edge cases) and
    -- these accesses are simply ignored by the hardware.
    -- The second case when the upper 8 bits are non zero should maybe never
    -- occur, this may be a problem with the softcore... This needs to be
    -- researched further...

    ----------------
    -- Edge Cases --
    ----------------
    edge_case_process : process(clk_68k_i) is
    begin
        if rising_edge(clk_68k_i) then
            edge_case_ack_s <= '0';
            if edge_case_enable_s = '1' then
                if (read_strobe_s = '1') or (write_strobe_s = '1') then
                    edge_case_ack_s <= '1';
                end if;
            end if; -- Enable
        end if; -- Rising Edge Clock
    end process edge_case_process;

end struct;
