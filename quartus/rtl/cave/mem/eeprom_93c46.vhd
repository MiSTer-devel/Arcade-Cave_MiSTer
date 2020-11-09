-------------------------------------------------------------------------------
--
-- Copyright (c) 2018 Rick Wertenbroek <rick.wertenbroek@gmail.com>
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
-- File         : eeprom_93c46.vhd
-- Description  : This a VHDL version of the 93c46 serial EEPROM. It has a
--                64x16 bit configuration.
--
--                TODO : Only read an write are supported for now, the rest
--                could be implemented quite fast since all the logic is
--                already written below.
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

-------------------------------------------------------------------------------
-- Block diagram
-------------------------------------------------------------------------------
--
--           +----------------+    +-----------+
--           |                |    |  Address  |
--           | Memory Array   |<---|  Decoder  |
--           |                |    |           |
--           +----------------+    +-----------+
--                    ^                 ^
--                    |                 |
--                    v                 |
--           +----------------+         |       +------------+
--           |                |         |       |   Output   |
--        +->| Data Register  |---------)------>|   Buffer   |---> DO
--        |  |                |         |       |            |
--        |  +----------------+         |       +------------+
--        |           ^                 |             ^
--  DI ---+           |                 |             |
--        |  +----------------+         |             |
--        +->|     Mode       |---------+             |
--           |    Decode      |                       |
--  CS ----->|     Logic      |-----------------------+
--           +----------------+
--                    ^
--                    |
--           +----------------+
--  CLK ---->|    Clock       |
--           |  Generator     |
--           +----------------+
--
-------------------------------------------------------------------------------

-------------------------------------------------------------------------------
-- Instruction Set
-------------------------------------------------------------------------------
--
-- +-------------+-----------+---------+-------------------+-----------+------------+----------+
-- | Instruction | Start bit |  Opcode |      Address      | Number of |  Data Out  | Req. Clk |
-- |             |           | OP1 OP2 |                   |  Data In  |            |  Cycles  |
-- +-------------+-----------+---------+-------------------+-----------+------------+----------+
-- | READ        | 1         |   1 0   | A5 A4 A3 A2 A1 A0 |    ---    |  D15 - D0  |    25    |
-- +-------------+-----------+---------+-------------------+-----------+------------+----------+
-- | WRITE       | 1         |   0 1   | A5 A4 A3 A2 A1 A0 |  D15 - D0 | (RDY/nBSY) |    25    |
-- +-------------+-----------+---------+-------------------+-----------+------------+----------+
-- | ERASE       | 1         |   1 1   | A5 A4 A3 A2 A1 A0 |    ---    | (RDY/nBSY) |     9    |
-- +-------------+-----------+---------+-------------------+-----------+------------+----------+
-- | EWEN        | 1         |   0 0   |  1  1  X  X  X  X |    ---    |   High-Z   |     9    |
-- +-------------+-----------+---------+-------------------+-----------+------------+----------+
-- | EWDS        | 1         |   0 0   |  0  0  X  X  X  X |    ---    |   High-Z   |     9    |
-- +-------------+-----------+---------+-------------------+-----------+------------+----------+
-- | ERAL        | 1         |   0 0   |  1  0  X  X  X  X |    ---    | (RDY/nBSY) |     9    |
-- +-------------+-----------+---------+-------------------+-----------+------------+----------+
-- | WRAL        | 1         |   0 0   |  0  1  X  X  X  X |  D15 - D0 | (RDY/nBSY) |    25    |
-- +-------------+-----------+---------+-------------------+-----------+------------+----------+
--
-------------------------------------------------------------------------------

-------------------------------------------------------------------------------
-- Functional Description
-------------------------------------------------------------------------------
-- Start condition :
-- The START bit is detected by the device if CS and DI are both HIGH with
-- respect to the positive edge of CLK for the first time.
--
-- Before a START condition is detected, CS, CLK, and DI may change in any
-- combination (except to that of a START condition), without resulting in any
-- device operation (READ, WRITE, ERASE, EWEN, EWDS, ERAL, and WRAL). As soon
-- as CS is HIGH, the device is no longer in the standby mode.
--
-- An instruction following a START condition will only be executed if the
-- required amount of opcode, address, and data bits for any particular
-- instruction is clocked in.
--
-- After execution of an instruction (i.e., clock in or out of the lat required
-- address or data bit) CLK and DI become don't care bits until a new start
-- condition is detected.
--
-- READ Mode :
-- The READ instruction outputs the serial data of the addressed memory
-- location on the DO pin. A dummy bit (logical 0) precedes the 16-bit output
-- string. The output data changes during the HIGH state of the system clock
-- (CLK). The dummy bit is output TPD after the positive edge of CLK, which was
-- used to clock in the last address bit (A0).
--
-- DO will got into HIGH-Z mode with the positive edge of the next CLK cycle.
-- This follows the output of the lat data bit D0 or the low going edge of CS,
-- which ever occurs first.
--
-- DO remains stable between CLK cycles for an unlimited time as long as CS
-- stays HIGH.
--
-- The most significant data bit (D15) is always output first, followed by the
-- lower significant bits (D14 - D0).
--
-- WRITE Mode :
-- The WRITE instruction is followed by 16 bits of data which are written into
-- the specified address. The most significant data bit (D15) has to be clocked
-- in first, followed by the lower significant data bits (D14 - D0). If a WRITE
-- instruction is recognized by the device and all data bits have been clocked
-- in, the device performs an automatic ERASE cycle on the specified address
-- before the data are written. The WRITE cycle is completely self-timed and
-- commences automatically after the rising edge of the CLK for the last data
-- bit (D0).
--
-- ERASE Mode :
-- The ERASE  instruction forces all the data bits of the specified address to
-- logical "1s". The ERASE cycle is completely self-timed and commences
-- automatically after the last address bit has been clocked in.
--
-- ERASE/WRITE Enable/Disable (EWEN, EWDS) :
-- The device is automatically in the ERASE/WRITE Disable mode (EWDS) after
-- power-up. Therefore, an EWEN instruction has to be performed before any
-- ERASE, WRITE, ERAL, WRAL instruction is executed by the device. For added
-- data protection, the device should be put in the ERASE/WRITE Disable mode
-- (EWDS) after programming operations are completed.
--
-- ERASE ALL (ERAL) :
-- The entire chip will be erased to logical "1s" if this instruciton is
-- received by the device and it is in the EWEN mode. The ERAL cycle is
-- completely self-timed and commences after the last dummy address bit has
-- been clocked in.
--
-- WRITE ALL (WRAL) :
-- The entire chip will be written with the data specified in that command. The
-- WRAL cycle is completely self-timed and commences after the rising edge of
-- the CLK for the last data bit (D0).
--
-- Note : The WRAL does not include an automatic ERASE cycle for the chip.
-- Therefore, the WRAL instruction must be preceded by an ERAL instruction and
-- the chip must be in the EWEN status in both cases.
--
-------------------------------------------------------------------------------

entity eeprom_93c46 is
    port (
        clk_i  : in  std_logic;
        cs_i   : in  std_logic;
        data_i : in  std_logic;
        data_o : out std_logic
    );
end entity eeprom_93c46;

architecture rtl of eeprom_93c46 is

    subtype register_t is std_logic_vector(15 downto 0);
    type reg_array_t is array (natural range <>) of register_t;
    type memory_action_t is (NONE, WRITE_REG, ERASE_REG, ERASE_ALL, WRITE_ALL);
    type write_en_mode_t is (EWDS, EWEN);
    type device_state_t is (STANDBY, DECODE_OP1, DECODE_OP2, DECODE_ADDR, DECODE_DATA, SEND_DATA);

    -- Memory elements
    signal memory_array_s      : reg_array_t(63 downto 0);
    signal sipo_reg_s          : std_logic_vector(14 downto 0);
    signal addr_sipo_reg_s     : std_logic_vector(5 downto 0);
    signal piso_reg_s          : std_logic_vector(15 downto 0);
    signal piso_en_s           : std_logic;
    signal write_en_mode_reg_s : write_en_mode_t;
    signal state_reg_s         : device_state_t;
    signal op_reg_s            : std_logic_vector(1 to 4);

    signal counter_s           : unsigned(5 downto 0);

    -- Signals
    signal next_state_s    : device_state_t;
    signal memory_action_s : memory_action_t;
    signal data_in_s       : std_logic_vector(15 downto 0);

    signal sipo_en_s       : std_logic;
    signal addr_sipo_en_s  : std_logic;
    signal data_incoming_s : std_logic;
    signal data_to_send_s  : std_logic;

    signal write_command_s : std_logic;
    signal read_command_s  : std_logic;

begin

    -------------------------------------
    -- Serial In Parallel Out Register --
    -------------------------------------
    sipo_process : process(clk_i, cs_i) is
    begin
        if cs_i = '0' then
            sipo_reg_s <= (others => '0');
        else
            if rising_edge(clk_i) then

                -- Data SIPO
                if sipo_en_s = '1' then
                    sipo_reg_s(0) <= data_i;
                    for i in 1 to sipo_reg_s'high loop
                        sipo_reg_s(i) <= sipo_reg_s(i-1);
                    end loop; -- SIPO
                end if; -- Enable

                -- Address SIPO
                if addr_sipo_en_s = '1' then
                    addr_sipo_reg_s(0) <= data_i;
                    for i in 1 to addr_sipo_reg_s'high loop
                        addr_sipo_reg_s(i) <= addr_sipo_reg_s(i-1);
                    end loop; -- SIPO
                end if; -- Enable

            end if; -- Rising Edge Clock
        end if; -- Chip Select
    end process sipo_process;

    -------------
    -- Control --
    -------------

    -- This could be redone with a SIPO and more elegantly (e.g., renaming the
    -- opcodes).
    op_reg_process : process(clk_i) is
    begin
        if rising_edge(clk_i) then
            if state_reg_s = DECODE_OP1 then
                op_reg_s(1) <= data_i;
            end if;

            if state_reg_s = DECODE_OP2 then
                op_reg_s(2) <= data_i;
            end if;

            if (state_reg_s = DECODE_ADDR) and (counter_s = 3) then
                op_reg_s(3) <= data_i;
            end if;

            if (state_reg_s = DECODE_ADDR) and (counter_s = 4) then
                op_reg_s(4) <= data_i;
            end if;
        end if;
    end process op_reg_process;

    -- WRITE and WRAL are the two commands that have data incoming
    data_incoming_s <= '1' when (op_reg_s(1) = '0') and ((op_reg_s(2) = '1') or ((op_reg_s(3) = '0') and (op_reg_s(4) = '1'))) else
                       '0';

    -- WRITE
    write_command_s <= '1' when (op_reg_s(1) = '0') and (op_reg_s(2) = '1') else
                       '0';

    -- READ - TODO : Could be merged with signal below
    read_command_s  <= '1' when (op_reg_s(1) = '1') and (op_reg_s(2) = '0') else
                       '0';

    -- There is data to send when doing a READ
    data_to_send_s  <= '1' when (op_reg_s(1) = '1') and (op_reg_s(2) = '0') else
                       '0';

    -- Shift registers enable signals
    sipo_en_s       <= '1' when state_reg_s = DECODE_DATA else
                       '0';
    addr_sipo_en_s  <= '1' when state_reg_s = DECODE_ADDR else
                       '0';
    piso_en_s       <= '1' when state_reg_s = SEND_DATA else
                       '0';

    -- FSM state register
    fsm_state_reg_process : process(clk_i, cs_i) is
    begin
        if cs_i = '0' then
            state_reg_s <= STANDBY;
        else
            if rising_edge(clk_i) then
                state_reg_s <= next_state_s;
            end if;
        end if;
    end process fsm_state_reg_process;

    -- FSM next state logic
    fsm_next_state_process : process(all) is
    begin

        -- Default behaviour is to stay in the same state
        next_state_s <= state_reg_s;

        case state_reg_s is
            when STANDBY =>
                -- If there is a start bit go to decode OP1
                if data_i = '1' then
                    next_state_s <= DECODE_OP1;
                end if;

            when DECODE_OP1 =>
                -- Then decode OP2
                next_state_s <= DECODE_OP2;

            when DECODE_OP2 =>
                -- Then decode the address
                next_state_s <= DECODE_ADDR;

            when DECODE_ADDR =>
                if counter_s = 8 then

                    -- If we need to decode data coming in
                    if data_incoming_s = '1' then
                        next_state_s <= DECODE_DATA;

                    -- If we need to send data out
                    elsif data_to_send_s = '1' then
                        next_state_s <= SEND_DATA;

                    -- If there is nothing more to do
                    else
                        next_state_s <= STANDBY;
                    end if;
                end if;

            when DECODE_DATA | SEND_DATA =>
                if counter_s = 24 then
                    next_state_s <= STANDBY;
                end if;

        end case;

    end process fsm_next_state_process;

    -------------
    -- counter --
    -------------
    counter_process : process(clk_i, cs_i) is
    begin
        if cs_i = '0' then
            counter_s <= (others => '0');
        else
            if rising_edge(clk_i) then
                if next_state_s /= STANDBY then
                    counter_s <= counter_s + 1;
                end if;
            end if;
        end if;
    end process counter_process;

    ------------------
    -- Memory Array --
    ------------------

    data_in_s <= sipo_reg_s & data_i;

    -- I decided to write a simple version using registers instead of BRAM since
    -- the size of the EEPROM is really small (64x16-bit)
    memory_array_process : process(clk_i) is
        variable index_v : natural;
    begin
        index_v := to_integer(unsigned(addr_sipo_reg_s));

        if rising_edge(clk_i) then
            case memory_action_s is
                when WRITE_REG =>
                    memory_array_s(index_v) <= data_in_s;
                when ERASE_REG =>
                    memory_array_s(index_v) <= (others => '1');
                when ERASE_ALL =>
                    for i in memory_array_s'low to memory_array_s'high loop
                        memory_array_s(i) <= (others => '1');
                    end loop;
                when WRITE_ALL =>
                    for i in memory_array_s'low to memory_array_s'high loop
                        memory_array_s(i) <= data_in_s;
                    end loop;
                when others => null;
            end case;
        end if;
    end process memory_array_process;

    -- Memory action
    memory_action_process : process(all) is
    begin
        -- Default action, do nothing
        memory_action_s <= NONE;

        -- For now only do WRITE_REG
        if counter_s = 24 then
            if write_command_s = '1' then
                memory_action_s <= WRITE_REG;
            end if;
        end if;

    end process memory_action_process;

    piso_process : process(clk_i) is
        variable index_v : natural;
    begin
        -- TODO : Check this timing
        index_v := to_integer(unsigned(addr_sipo_reg_s(4 downto 0) & data_i));

        if rising_edge(clk_i) then
            if counter_s = 8 then
                -- Load
                if read_command_s = '1' then
                    piso_reg_s <= memory_array_s(index_v);
                end if;
            else
                -- Shift
                for i in 1 to piso_reg_s'high loop
                    piso_reg_s(i) <= piso_reg_s(i-1);
                end loop;
            end if;
        end if;
    end process piso_process;

    data_o <= piso_reg_s(15) when state_reg_s = SEND_DATA else
              '1'; -- Here we deviate from the datasheet, instead of going hi-Z
                   -- we stay at '1', this indicates the eeprom is ready, if
                   -- queried.

end rtl;
