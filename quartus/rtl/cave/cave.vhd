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
        -- Player input signals
        player_player1           : in  std_logic_vector(8 downto 0);
        player_player2           : in  std_logic_vector(8 downto 0);
        player_pause             : in  std_logic;
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
    -- YMZ RAM
    constant YMZ_RAM_LOG_SIZE_C     : natural := 2;   -- 4B
    signal ymz_ram_enable_s         : std_logic;
    signal ymz_ram_ack_s            : std_logic;
    signal ymz_ram_data_o_s         : word_t;
    -- IRQ Cause (read only)
    signal irq_cause_enable_s       : std_logic;
    signal irq_cause_ack_s          : std_logic;
    signal irq_cause_data_o_s       : word_t;
    -- Inputs 0
    constant IN_0_LOG_SIZE_C        : natural := 1;   -- 2B
    signal in_0_enable_s            : std_logic;
    signal in_0_ack_s               : std_logic;
    signal in_0_data_o_s            : word_t;
    -- Inputs 1
    constant IN_1_LOG_SIZE_C        : natural := 1;   -- 2B
    signal in_1_enable_s            : std_logic;
    signal in_1_ack_s               : std_logic;
    signal in_1_data_o_s            : word_t;
    -- EEPROM
    constant EEPROM_LOG_SIZE_C      : natural := 1;   -- 2B
    signal eeprom_enable_s          : std_logic;
    signal eeprom_ack_s             : std_logic;
    signal eeprom_data_o_s          : word_t;
    signal eeprom_ci_s              : std_logic;
    signal eeprom_cs_s              : std_logic;
    signal eeprom_di_s              : std_logic;
    signal eeprom_do_s              : std_logic;
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
    memory_bus_ack_s <= ymz_ram_ack_s     or
                        irq_cause_ack_s   or
                        in_0_ack_s        or
                        in_1_ack_s        or
                        eeprom_ack_s      or
                        edge_case_ack_s   or
                        other_ack_s;

    memBus_ack <= memory_bus_ack_s;

    -- "OR" everything together to create the "OR'ed" bus
    memory_bus_data_s <= ymz_ram_data_o_s     or
                         irq_cause_data_o_s   or
                         in_0_data_o_s        or
                         in_1_data_o_s        or
                         eeprom_data_o_s      or
                         other_data_o_s;

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

    -- YMZ RAM              0x300000 - 0x300003
    ymz_ram_enable_s     <= '1' when addr_68k_s(31 downto YMZ_RAM_LOG_SIZE_C) = x"0030000" & "00" else
                            '0';
    -- IRQ Cause (same about redundancy)
    --                      0x800000 - 0x800007
    irq_cause_enable_s   <= '1' when (addr_68k_s(31 downto 3) = x"0080000" & "0") and (read_n_write_68k_s = '1') else
                            '0';
    -- Inputs 0             0xd00000 - 0xd00001
    in_0_enable_s        <= '1' when addr_68k_s(31 downto IN_0_LOG_SIZE_C) = x"00d0000" & "000" else
                            '0';
    -- Inputs 1             0xd00000 - 0xd00003
    in_1_enable_s        <= '1' when addr_68k_s(31 downto IN_1_LOG_SIZE_C) = x"00d0000" & "001" else
                            '0';
    -- EEPROM               0xe00000 - 0xe00001
    eeprom_enable_s      <= '1' when addr_68k_s(31 downto EEPROM_LOG_SIZE_C) = x"00e0000" & "000" else
                            '0';
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

    -------------
    -- YMZ280b --
    -------------
    ymz280b_block : block
    begin
        ymz280b_regs : entity work.true_dual_port_ram
            generic map (
                ADDR_WIDTH_A => YMZ_RAM_LOG_SIZE_C-1,
                DATA_WIDTH_A => DDP_WORD_WIDTH,
                ADDR_WIDTH_B => YMZ_RAM_LOG_SIZE_C-1,
                DATA_WIDTH_B => DDP_WORD_WIDTH)
            port map (
                clk_a  => clk_68k_i,
                cs_a   => ymz_ram_enable_s,
                wr_a   => write_strobe_s,
                rd_a   => read_strobe_s,
                addr_a => addr_68k_s(YMZ_RAM_LOG_SIZE_C-1 downto 1),
                din_a  => data_out_68k_s,
                dout_a => ymz_ram_data_o_s,
                ack_a  => ymz_ram_ack_s,
                clk_b  => clk_i,
                addr_b => to_unsigned(0, YMZ_RAM_LOG_SIZE_C-1),
                dout_b => open);
    end block ymz280b_block;

    graphic_processor_block : block
    begin

        ---------------
        -- IRQ Cause --
        ---------------
        -- TODO (Temporary)
        irq_cause_process : process(clk_68k_i) is
        begin
            if rising_edge(clk_68k_i) then
                irq_cause_ack_s       <= '0';
                irq_cause_data_o_s    <= (others => '0');
                if irq_cause_enable_s = '1' then
                    if read_strobe_s = '1' then
                        irq_cause_data_o_s <= x"0003";  -- Active low
                        irq_cause_ack_s    <= '1';
                    end if;
                end if;
            end if;  -- Rising Edge Clock
        end process irq_cause_process;

    end block graphic_processor_block;

    -----------------
    -- Input Ports --
    -----------------
    input_ports_block : block
        signal in_0_reg_s : word_t;
        signal in_1_reg_s : word_t;
    begin
        -- Enable for the OR'ed bus
        in_0_data_o_s <= in_0_reg_s when in_0_enable_s = '1' else (others => '0');
        in_1_data_o_s <= in_1_reg_s when in_1_enable_s = '1' else (others => '0');

        in_process : process(clk_68k_i) is
        begin
            if rising_edge(clk_68k_i) then
                in_0_ack_s <= '0';
                in_1_ack_s <= '0';
                in_0_reg_s <= "1111111" & (not player_player1(8 downto 0));
                in_1_reg_s <= "1111" & eeprom_do_s & "11" & (not player_player2(8 downto 0));
                if read_strobe_s = '1' then
                    if in_0_enable_s = '1' then
                        in_0_ack_s <= '1';
                    end if;
                    if in_1_enable_s = '1' then
                        in_1_ack_s <= '1';
                    end if;
                end if;
            end if;
        end process in_process;

    end block input_ports_block;

    ------------
    -- EEPROM --
    ------------
    eeprom_generate : if INCLUDE_EEPROM_G generate

        eeprom : entity work.eeprom_93c46
            port map (
                clk_i  => eeprom_ci_s,
                cs_i   => eeprom_cs_s,
                data_i => eeprom_di_s,
                data_o => eeprom_do_s);

    else generate

        eeprom_do_s <= '0';

    end generate eeprom_generate;

    eeprom_process : process(clk_68k_i) is
    begin
        if rising_edge(clk_68k_i) then
            eeprom_ack_s <= '0';
            if eeprom_enable_s = '1' then
                -- I believe it is possible that this is never ever read
                if read_strobe_s = '1' then
                    eeprom_data_o_s <= x"0000";
                    eeprom_ack_s    <= '1';
                elsif write_strobe_s = '1' then
                    eeprom_cs_s  <= data_out_68k_s(1);
                    eeprom_ci_s  <= data_out_68k_s(2);
                    eeprom_di_s  <= data_out_68k_s(3);
                    eeprom_ack_s <= '1';
                end if;
            else
                eeprom_data_o_s <= (others => '0');
            end if;  -- Enable
        end if;  -- Rising Edge Clock
    end process eeprom_process;

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
