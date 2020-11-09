------------------------------------------------------------------------------
------------------------------------------------------------------------------
--                                                                          --
-- This is the 68000 software compatible Kernal of TG68                     --
--                                                                          --
-- Copyright (c) 2007-2010 Tobias Gubener <tobiflex@opencores.org>          --
--                                                                          --
-- This source file is free software: you can redistribute it and/or modify --
-- it under the terms of the GNU Lesser General Public License as published --
-- by the Free Software Foundation, either version 3 of the License, or     --
-- (at your option) any later version.                                      --
--                                                                          --
-- This source file is distributed in the hope that it will be useful,      --
-- but WITHOUT ANY WARRANTY; without even the implied warranty of           --
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            --
-- GNU General Public License for more details.                             --
--                                                                          --
-- You should have received a copy of the GNU General Public License        --
-- along with this program.  If not, see <http://www.gnu.org/licenses/>.    --
--                                                                          --
------------------------------------------------------------------------------
------------------------------------------------------------------------------
-- Revision 1.08.2 2019
-- By Rick Wertenbroek
-- Added missing (unassigned) bits on interupt vector address
--
-- Revision 1.08.1 2019
-- By Rick Wertenbroek
-- Fixed identation / beautify
-- Added reset on registers
--
-- Revision 1.08 2010/06/14
-- Bugfix Movem with regmask==xFFFF
-- Add missing Illegal $4AFC
--
-- Revision 1.07 2009/10/02
-- Bugfix Movem with regmask==x0000
--
-- Revision 1.06 2009/02/10
-- Bugfix shift and rotations opcodes when the bitcount and the data are in the same register:
-- Example lsr.l D2,D2
-- Thanks to Peter Graf for report
--
-- Revision 1.05 2009/01/26
-- Implement missing RTR
-- Thanks to Peter Graf for report
--
-- Revision 1.04 2007/12/29
-- size improvement
-- change signal "microaddr" to one hot state machine
--
-- Revision 1.03 2007/12/21
-- Thanks to Andreas Ehliar
-- Split regfile to use blockram for registers
-- insert "WHEN OTHERS => null;" on END CASE;
--
-- Revision 1.02 2007/12/17
-- Bugfix jsr  nn.w
--
-- Revision 1.01 2007/11/28
-- add MOVEP
-- Bugfix Interrupt in MOVEQ
--
-- Revision 1.0 2007/11/05
-- Clean up code and first release
--
-- known bugs/todo:
-- Add CHK INSTRUCTION
-- full decode ILLEGAL INSTRUCTIONS
-- Add FC Output
-- add odd Address test
-- add TRACE


library ieee;
use ieee.std_logic_1164.all;
use ieee.std_logic_unsigned.all;

entity TG68_fast is
    port(clk        : in     std_logic;
         reset      : in     std_logic;                            --low active
         clkena_in  : in     std_logic                    := '1';
         data_in    : in     std_logic_vector(15 downto 0);
         IPL        : in     std_logic_vector(2 downto 0) := "111";
         test_IPL   : in     std_logic                    := '0';  --only for debugging
         address    : out    std_logic_vector(31 downto 0);
         data_write : out    std_logic_vector(15 downto 0);
         state_out  : out    std_logic_vector(1 downto 0);
         LDS, UDS   : out    std_logic;
         decodeOPC  : buffer std_logic;
         wr         : out    std_logic;
         -- Added debug signals
         TG68_PC_o  : out    std_logic_vector(31 downto 0);
         TG68_PCW_o : out    std_logic
         );
end TG68_fast;

architecture logic of TG68_fast is

    signal state                         : std_logic_vector(1 downto 0);
    signal clkena                        : std_logic;
    signal TG68_PC                       : std_logic_vector(31 downto 0);
    signal TG68_PC_add                   : std_logic_vector(31 downto 0);
    signal memaddr                       : std_logic_vector(31 downto 0);
    signal memaddr_in                    : std_logic_vector(31 downto 0);
    signal ea_data                       : std_logic_vector(31 downto 0);
    signal ea_data_OP1                   : std_logic;
    signal setaddrlong                   : std_logic;
    signal OP1out, OP2out                : std_logic_vector(31 downto 0);
    signal OP1outbrief                   : std_logic_vector(15 downto 0);
    signal OP1in                         : std_logic_vector(31 downto 0);
    signal data_write_tmp                : std_logic_vector(31 downto 0);
    signal Xtmp                          : std_logic_vector(31 downto 0);
    signal PC_dataa, PC_datab, PC_result : std_logic_vector(31 downto 0);
    signal setregstore                   : std_logic;
    signal datatype                      : std_logic_vector(1 downto 0);
    signal longread                      : std_logic;
    signal longreaddirect                : std_logic;
    signal long_done                     : std_logic;
    signal nextpass                      : std_logic;
    signal setnextpass                   : std_logic;
    signal setdispbyte                   : std_logic;
    signal setdisp                       : std_logic;
    signal setdispbrief                  : std_logic;
    signal regdirectsource               : std_logic;
    signal endOPC                        : std_logic;
    signal postadd                       : std_logic;
    signal presub                        : std_logic;
    signal addsub_a                      : std_logic_vector(31 downto 0);
    signal addsub_b                      : std_logic_vector(31 downto 0);
    signal addsub_q                      : std_logic_vector(31 downto 0);
    signal briefext                      : std_logic_vector(31 downto 0);
    signal setbriefext                   : std_logic;
    signal addsub                        : std_logic;
    signal c_in                          : std_logic_vector(3 downto 0);
    signal c_out                         : std_logic_vector(2 downto 0);
    signal add_result                    : std_logic_vector(33 downto 0);
    signal addsub_ofl                    : std_logic_vector(2 downto 0);
    signal flag_z                        : std_logic_vector(2 downto 0);

    signal last_data_read : std_logic_vector(15 downto 0);
    signal data_read      : std_logic_vector(31 downto 0);

    signal registerin         : std_logic_vector(31 downto 0);
    signal reg_QA             : std_logic_vector(31 downto 0);
    signal reg_QB             : std_logic_vector(31 downto 0);
    signal Hwrena, Lwrena     : std_logic;
    signal Regwrena           : std_logic;
    signal rf_dest_addr       : std_logic_vector(6 downto 0);
    signal rf_source_addr     : std_logic_vector(6 downto 0);
    signal rf_dest_addr_tmp   : std_logic_vector(6 downto 0);
    signal rf_source_addr_tmp : std_logic_vector(6 downto 0);
    signal opcode             : std_logic_vector(15 downto 0);
    signal laststate          : std_logic_vector(1 downto 0);
    signal setstate           : std_logic_vector(1 downto 0);

    signal mem_address    : std_logic_vector(31 downto 0);
    signal memaddr_a      : std_logic_vector(31 downto 0);
    signal mem_data_read  : std_logic_vector(31 downto 0);
    signal mem_data_write : std_logic_vector(31 downto 0);
    signal set_mem_rega   : std_logic;
    signal data_read_ram  : std_logic_vector(31 downto 0);
    signal data_read_uart : std_logic_vector(7 downto 0);

    signal counter_reg : std_logic_vector(31 downto 0);

    signal TG68_PC_br8      : std_logic;
    signal TG68_PC_brw      : std_logic;
    signal TG68_PC_nop      : std_logic;
    signal setgetbrief      : std_logic;
    signal getbrief         : std_logic;
    signal brief            : std_logic_vector(15 downto 0);
    signal dest_areg        : std_logic;
    signal source_areg      : std_logic;
    signal data_is_source   : std_logic;
    signal set_store_in_tmp : std_logic;
    signal store_in_tmp     : std_logic;
    signal write_back       : std_logic;
    signal setaddsub        : std_logic;
    signal setstackaddr     : std_logic;
    signal writePC          : std_logic;
    signal writePC_add      : std_logic;
    signal set_TG68_PC_dec  : std_logic;
    signal TG68_PC_dec      : std_logic_vector(1 downto 0);
    signal directPC         : std_logic;
    signal set_directPC     : std_logic;
    signal execOPC          : std_logic;
    signal fetchOPC         : std_logic;
    signal Flags            : std_logic_vector(15 downto 0);  --T.S..III ...XNZVC
    signal set_Flags        : std_logic_vector(3 downto 0);   --NZVC
    signal exec_ADD         : std_logic;
    signal exec_OR          : std_logic;
    signal exec_AND         : std_logic;
    signal exec_EOR         : std_logic;
    signal exec_MOVE        : std_logic;
    signal exec_MOVEQ       : std_logic;
    signal exec_MOVESR      : std_logic;
    signal exec_DIRECT      : std_logic;
    signal exec_ADDQ        : std_logic;
    signal exec_CMP         : std_logic;
    signal exec_ROT         : std_logic;
    signal exec_exg         : std_logic;
    signal exec_swap        : std_logic;
    signal exec_write_back  : std_logic;
    signal exec_tas         : std_logic;
    signal exec_EXT         : std_logic;
    signal exec_ABCD        : std_logic;
    signal exec_SBCD        : std_logic;
    signal exec_MULU        : std_logic;
    signal exec_DIVU        : std_logic;
    signal exec_Scc         : std_logic;
    signal exec_CPMAW       : std_logic;
    signal set_exec_ADD     : std_logic;
    signal set_exec_OR      : std_logic;
    signal set_exec_AND     : std_logic;
    signal set_exec_EOR     : std_logic;
    signal set_exec_MOVE    : std_logic;
    signal set_exec_MOVEQ   : std_logic;
    signal set_exec_MOVESR  : std_logic;
    signal set_exec_ADDQ    : std_logic;
    signal set_exec_CMP     : std_logic;
    signal set_exec_ROT     : std_logic;
    signal set_exec_tas     : std_logic;
    signal set_exec_EXT     : std_logic;
    signal set_exec_ABCD    : std_logic;
    signal set_exec_SBCD    : std_logic;
    signal set_exec_MULU    : std_logic;
    signal set_exec_DIVU    : std_logic;
    signal set_exec_Scc     : std_logic;
    signal set_exec_CPMAW   : std_logic;

    signal condition           : std_logic;
    signal OP2out_one          : std_logic;
    signal OP1out_zero         : std_logic;
    signal ea_to_pc            : std_logic;
    signal ea_build            : std_logic;
    signal ea_only             : std_logic;
    signal get_ea_now          : std_logic;
    signal source_lowbits      : std_logic;
    signal dest_hbits          : std_logic;
    signal rot_rot             : std_logic;
    signal rot_lsb             : std_logic;
    signal rot_msb             : std_logic;
    signal rot_XC              : std_logic;
    signal set_rot_nop         : std_logic;
    signal rot_nop             : std_logic;
    signal rot_out             : std_logic_vector(31 downto 0);
    signal rot_bits            : std_logic_vector(1 downto 0);
    signal rot_cnt             : std_logic_vector(5 downto 0);
    signal set_rot_cnt         : std_logic_vector(5 downto 0);
    signal movem_busy          : std_logic;
    signal set_movem_busy      : std_logic;
    signal movem_addr          : std_logic;
    signal movem_regaddr       : std_logic_vector(3 downto 0);
    signal movem_mask          : std_logic_vector(15 downto 0);
    signal set_get_movem_mask  : std_logic;
    signal get_movem_mask      : std_logic;
    signal maskzero            : std_logic;
    signal test_maskzero       : std_logic;
    signal movem_muxa          : std_logic_vector(7 downto 0);
    signal movem_muxb          : std_logic_vector(3 downto 0);
    signal movem_muxc          : std_logic_vector(1 downto 0);
    signal movem_presub        : std_logic;
    signal save_memaddr        : std_logic;
    signal movem_bits          : std_logic_vector(4 downto 0);
    signal ea_calc_b           : std_logic_vector(31 downto 0);
    signal set_mem_addsub      : std_logic;
    signal bit_bits            : std_logic_vector(1 downto 0);
    signal bit_number_reg      : std_logic_vector(4 downto 0);
    signal bit_number          : std_logic_vector(4 downto 0);
    signal exec_Bits           : std_logic;
    signal bits_out            : std_logic_vector(31 downto 0);
    signal one_bit_in          : std_logic;
    signal one_bit_out         : std_logic;
    signal set_get_bitnumber   : std_logic;
    signal get_bitnumber       : std_logic;
    signal mem_byte            : std_logic;
    signal wait_mem_byte       : std_logic;
    signal movepl              : std_logic;
    signal movepw              : std_logic;
    signal set_movepl          : std_logic;
    signal set_movepw          : std_logic;
    signal set_direct_data     : std_logic;
    signal use_direct_data     : std_logic;
    signal direct_data         : std_logic;
    signal set_get_extendedOPC : std_logic;
    signal get_extendedOPC     : std_logic;
    signal setstate_delay      : std_logic_vector(1 downto 0);
    signal setstate_mux        : std_logic_vector(1 downto 0);
    signal use_XZFlag          : std_logic;
    signal use_XFlag           : std_logic;

    signal dummy_a        : std_logic_vector(8 downto 0);
    signal niba_l         : std_logic_vector(5 downto 0);
    signal niba_h         : std_logic_vector(5 downto 0);
    signal niba_lc        : std_logic;
    signal niba_hc        : std_logic;
    signal bcda_lc        : std_logic;
    signal bcda_hc        : std_logic;
    signal dummy_s        : std_logic_vector(8 downto 0);
    signal nibs_l         : std_logic_vector(5 downto 0);
    signal nibs_h         : std_logic_vector(5 downto 0);
    signal nibs_lc        : std_logic;
    signal nibs_hc        : std_logic;
    signal dummy_mulu     : std_logic_vector(31 downto 0);
    signal dummy_div      : std_logic_vector(31 downto 0);
    signal dummy_div_sub  : std_logic_vector(16 downto 0);
    signal dummy_div_over : std_logic_vector(16 downto 0);
    signal set_V_Flag     : std_logic;
    signal OP1sign        : std_logic;
    signal set_sign       : std_logic;
    signal sign           : std_logic;
    signal sign2          : std_logic;
    signal muls_msb       : std_logic;
    signal mulu_reg       : std_logic_vector(31 downto 0);
    signal div_reg        : std_logic_vector(31 downto 0);
    signal div_sign       : std_logic;
    signal div_quot       : std_logic_vector(31 downto 0);
    signal div_ovl        : std_logic;
    signal pre_V_Flag     : std_logic;
    signal set_vectoraddr : std_logic;
    signal writeSR        : std_logic;
    signal trap_illegal   : std_logic;
    signal trap_priv      : std_logic;
    signal trap_1010      : std_logic;
    signal trap_1111      : std_logic;
    signal trap_trap      : std_logic;
    signal trap_trapv     : std_logic;
    signal trap_interrupt : std_logic;
    signal trapmake       : std_logic;
    signal trapd          : std_logic;
--   signal trap_PC        : std_logic_vector(31 downto 0);
    signal trap_SR        : std_logic_vector(15 downto 0);

    signal set_directSR       : std_logic;
    signal directSR           : std_logic;
    signal set_directCCR      : std_logic;
    signal directCCR          : std_logic;
    signal set_stop           : std_logic;
    signal stop               : std_logic;
    signal trap_vector        : std_logic_vector(31 downto 0);
    signal to_USP             : std_logic;
    signal from_USP           : std_logic;
    signal to_SR              : std_logic;
    signal from_SR            : std_logic;
    signal illegal_write_mode : std_logic;
    signal illegal_read_mode  : std_logic;
    signal illegal_byteaddr   : std_logic;
    signal use_SP             : std_logic;

    signal no_Flags     : std_logic;
    signal IPL_nr       : std_logic_vector(2 downto 0);
    signal rIPL_nr      : std_logic_vector(2 downto 0);
    signal interrupt    : std_logic;
    signal SVmode       : std_logic;
    signal trap_chk     : std_logic;
    signal test_delay   : std_logic_vector(2 downto 0);
    signal set_PCmarker : std_logic;
    signal PCmarker     : std_logic;
    signal set_Z_error  : std_logic;
    signal Z_error      : std_logic;

    type micro_states is (idle, nop, ld_nn, st_nn, ld_dAn1, ld_dAn2, ld_AnXn1, ld_AnXn2, ld_AnXn3, st_dAn1, st_dAn2,
                          st_AnXn1, st_AnXn2, st_AnXn3, bra1, bra2, bsr1, bsr2, dbcc1, dbcc2,
                          movem, andi, op_AxAy, cmpm, link, int1, int2, int3, int4, rte, trap1, trap2, trap3,
                          movep1, movep2, movep3, movep4, movep5, init1, init2,
                          mul1, mul2, mul3, mul4, mul5, mul6, mul7, mul8, mul9, mul10, mul11, mul12, mul13, mul14, mul15,
                          div1, div2, div3, div4, div5, div6, div7, div8, div9, div10, div11, div12, div13, div14, div15);
    signal micro_state      : micro_states;
    signal next_micro_state : micro_states;

    type regfile_t is array(0 to 16) of std_logic_vector(15 downto 0);
    signal regfile_low  : regfile_t;
    signal regfile_high : regfile_t;
    signal RWindex_A    : integer range 0 to 16;
    signal RWindex_B    : integer range 0 to 16;

    signal execOPC_old  : std_logic;
begin

    -- Debug signals
    process (clk)
    begin
        if rising_edge(clk) then
            execOPC_old <= execOPC;
        end if;
    end process;

    TG68_PCW_o <= '1' when (execOPC = '1') and (execOPC_old = '0') else
                  '0';
    TG68_PC_o  <= TG68_PC;

-----------------------------------------------------------------------------
-- Registerfile
-----------------------------------------------------------------------------

    RWindex_A <= conv_integer(rf_dest_addr(4)&(rf_dest_addr(3 downto 0) xor "1111"));
    RWindex_B <= conv_integer(rf_source_addr(4)&(rf_source_addr(3 downto 0) xor "1111"));

    process (clk)
    begin
        if falling_edge(clk) then
            if clkena = '1' then
                reg_QA <= regfile_high(RWindex_A) & regfile_low(RWindex_A);
                reg_QB <= regfile_high(RWindex_B) & regfile_low(RWindex_B);
            end if;
        end if;
        if rising_edge(clk) then
            if reset = '0' then -- RWK : Added to be able to do arith. on regs
                                -- at reset in simulation
                regfile_low  <= (others => (others => '0'));
                regfile_high <= (others => (others => '0'));
            elsif clkena = '1' then
                if Lwrena = '1' then
                    regfile_low(RWindex_A) <= registerin(15 downto 0);
                end if;
                if Hwrena = '1' then
                    regfile_high(RWindex_A) <= registerin(31 downto 16);
                end if;
            end if;
        end if;
    end process;



    address   <= TG68_PC when state = "00"                                                             else X"ffffffff" when state = "01" else memaddr;
    LDS       <= '0'     when (datatype /= "00" or state = "00" or memaddr(0) = '1') and state /= "01" else '1';
    UDS       <= '0'     when (datatype /= "00" or state = "00" or memaddr(0) = '0') and state /= "01" else '1';
    state_out <= state;
    wr        <= '0'     when state = "11"                                                             else '1';
    IPL_nr    <= not IPL;


-----------------------------------------------------------------------------
-- "ALU"
-----------------------------------------------------------------------------
    process (addsub_a, addsub_b, addsub, add_result, c_in)
    begin
        if addsub = '1' then            --ADD
            add_result <= (('0'&addsub_a&c_in(0))+('0'&addsub_b&c_in(0)));
        else                            --SUB
            add_result <= (('0'&addsub_a&'0')-('0'&addsub_b&c_in(0)));
        end if;
        addsub_q      <= add_result(32 downto 1);
        c_in(1)       <= add_result(9) xor addsub_a(8) xor addsub_b(8);
        c_in(2)       <= add_result(17) xor addsub_a(16) xor addsub_b(16);
        c_in(3)       <= add_result(33);
        addsub_ofl(0) <= (c_in(1) xor add_result(8) xor addsub_a(7) xor addsub_b(7));  --V Byte
        addsub_ofl(1) <= (c_in(2) xor add_result(16) xor addsub_a(15) xor addsub_b(15));  --V Word
        addsub_ofl(2) <= (c_in(3) xor add_result(32) xor addsub_a(31) xor addsub_b(31));  --V Long
        c_out         <= c_in(3 downto 1);
    end process;

-----------------------------------------------------------------------------
-- MEM_IO
-----------------------------------------------------------------------------
    process (clk, reset, clkena_in, opcode, rIPL_nr, longread, get_extendedOPC, memaddr, memaddr_a, set_mem_addsub, movem_presub,
             movem_busy, state, PCmarker, execOPC, datatype, setdisp, setdispbrief, briefext, setdispbyte, brief,
             set_mem_rega, reg_QA, setaddrlong, data_read, decodeOPC, TG68_PC, data_in, long_done, last_data_read, mem_byte,
             data_write_tmp, addsub_q, set_vectoraddr, trap_vector, interrupt)
    begin
        clkena <= clkena_in and not longread and not get_extendedOPC;

        if rising_edge(clk) then
            if clkena = '1' then
                trap_vector(31 downto 8) <= (others => '0');
                --              IF trap_addr_fault='1' THEN
                --                      trap_vector(7 downto 0) <= X"08";
                --              END IF;
                --              IF trap_addr_error='1' THEN
                --                      trap_vector(7 downto 0) <= X"0C";
                --              END IF;
                if trap_illegal = '1' then
                    trap_vector(7 downto 0) <= X"10";
                end if;
                if z_error = '1' then
                    trap_vector(7 downto 0) <= X"14";
                end if;
--                              IF trap_chk='1' THEN
--                                      trap_vector(7 downto 0) <= X"18";
--                              END IF;
                if trap_trapv = '1' then
                    trap_vector(7 downto 0) <= X"1C";
                end if;
                if trap_priv = '1' then
                    trap_vector(7 downto 0) <= X"20";
                end if;
                --              IF trap_trace='1' THEN
                --                      trap_vector(7 downto 0) <= X"24";
                --              END IF;
                if trap_1010 = '1' then
                    trap_vector(7 downto 0) <= X"28";
                end if;
                if trap_1111 = '1' then
                    trap_vector(7 downto 0) <= X"2C";
                end if;
                if trap_trap = '1' then
                    trap_vector(7 downto 0) <= "10" & opcode(3 downto 0) & "00"; -- RWK Fixed missing (unassigned) bits
                end if;
                if interrupt = '1' then
                    trap_vector(7 downto 0) <= "011" & rIPL_nr & "00"; -- RWK Fixed missing (unassigned) bits
                end if;
            end if;
        end if;

        memaddr_a(3 downto 0)   <= "0000";
        memaddr_a(7 downto 4)   <= (others => memaddr_a(3));
        memaddr_a(15 downto 8)  <= (others => memaddr_a(7));
        memaddr_a(31 downto 16) <= (others => memaddr_a(15));
        if movem_presub = '1' then
            if movem_busy = '1' or longread = '1' then
                memaddr_a(3 downto 0) <= "1110";
            end if;
        elsif state(1) = '1' or (get_extendedOPC = '1' and PCmarker = '1') then
            memaddr_a(1) <= '1';
        elsif execOPC = '1' then
            if datatype = "10" then
                memaddr_a(3 downto 0) <= "1100";
            else
                memaddr_a(3 downto 0) <= "1110";
            end if;
        elsif setdisp = '1' then
            if setdispbrief = '1' then
                memaddr_a <= briefext;
            elsif setdispbyte = '1' then
                memaddr_a(7 downto 0) <= brief(7 downto 0);
            else
                memaddr_a(15 downto 0) <= brief;
            end if;
        end if;

        memaddr_in <= memaddr+memaddr_a;
        if longread = '0' then
            if set_mem_addsub = '1' then
                memaddr_in <= addsub_q;
            elsif set_vectoraddr = '1' then
                memaddr_in <= trap_vector;
            elsif interrupt = '1' then
                memaddr_in <= "1111111111111111111111111111"&rIPL_nr&'0';
            elsif set_mem_rega = '1' then
                memaddr_in <= reg_QA;
            elsif setaddrlong = '1' and longread = '0' then
                memaddr_in <= data_read;
            elsif decodeOPC = '1' then
                memaddr_in <= TG68_PC;
            end if;
        end if;

        data_read(15 downto 0)  <= data_in;
        data_read(31 downto 16) <= (others => data_in(15));
        if long_done = '1' then
            data_read(31 downto 16) <= last_data_read;
        end if;
        if mem_byte = '1' and memaddr(0) = '0' then
            data_read(7 downto 0) <= data_in(15 downto 8);
        end if;

        if longread = '1' then
            data_write <= data_write_tmp(31 downto 16);
        else
            data_write(7 downto 0) <= data_write_tmp(7 downto 0);
            if mem_byte = '1' then
                data_write(15 downto 8) <= data_write_tmp(7 downto 0);
            else
                data_write(15 downto 8) <= data_write_tmp(15 downto 8);
                if datatype = "00" then
                    data_write(7 downto 0) <= data_write_tmp(15 downto 8);
                end if;
            end if;
        end if;

        if reset = '0' then
            longread  <= '0';
            long_done <= '0';
        elsif rising_edge(clk) then
            if clkena_in = '1' then
                last_data_read <= data_in;
                long_done      <= longread;
                if get_extendedOPC = '0' or (get_extendedOPC = '1' and PCmarker = '1') then
                    memaddr <= memaddr_in;
                end if;
                if get_extendedOPC = '0' then

                    if ((setstate_mux(1) = '1' and datatype = "10") or longreaddirect = '1') and longread = '0' and interrupt = '0' then
                        longread <= '1';
                    else
                        longread <= '0';
                    end if;
                end if;

            end if;
        end if;
    end process;
-----------------------------------------------------------------------------
-- brief
-----------------------------------------------------------------------------
    process (clk, brief, OP1out)
    begin
        if brief(11) = '1' then
            OP1outbrief <= OP1out(31 downto 16);
        else
            OP1outbrief <= (others => OP1out(15));
        end if;
        if rising_edge(clk) then
            if clkena = '1' then
                briefext <= OP1outbrief&OP1out(15 downto 0);
--                              CASE brief(10 downto 9) IS
--                                      WHEN "00" => briefext <= OP1outbrief&OP1out(15 downto 0);
--                                      WHEN "01" => briefext <= OP1outbrief(14 downto 0)&OP1out(15 downto 0)&'0';
--                                      WHEN "10" => briefext <= OP1outbrief(13 downto 0)&OP1out(15 downto 0)&"00";
--                                      WHEN "11" => briefext <= OP1outbrief(12 downto 0)&OP1out(15 downto 0)&"000";
--                              END CASE;
            end if;
        end if;
    end process;

-----------------------------------------------------------------------------
-- PC Calc + fetch opcode
-----------------------------------------------------------------------------
    process (clk, reset, opcode, TG68_PC, TG68_PC_dec, TG68_PC_br8, TG68_PC_brw, PC_dataa, PC_datab, execOPC, last_data_read, get_extendedOPC,
             setstate_delay, setstate)
    begin
        PC_dataa               <= TG68_PC;
        PC_datab(2 downto 0)   <= "010";
        PC_datab(7 downto 3)   <= (others => PC_datab(2));
        PC_datab(15 downto 8)  <= (others => PC_datab(7));
        PC_datab(31 downto 16) <= (others => PC_datab(15));
        if execOPC = '0' then
            if TG68_PC_br8 = '1' then
                PC_datab(7 downto 0) <= opcode(7 downto 0);
            end if;
            if TG68_PC_dec(1) = '1' then
                PC_datab(2) <= '1';
            end if;
            if TG68_PC_brw = '1' then
                PC_datab(15 downto 0) <= last_data_read(15 downto 0);
            end if;
        end if;
        TG68_PC_add <= PC_dataa+PC_datab;

        if get_extendedOPC = '1' then
            setstate_mux <= setstate_delay;
        else
            setstate_mux <= setstate;
        end if;


        if reset = '0' then
            opcode(15 downto 12) <= X"7";   --moveq
            opcode(8 downto 6)   <= "010";  --long
            TG68_PC              <= (others => '0');
            state                <= "01";
            decodeOPC            <= '0';
            fetchOPC             <= '0';
            endOPC               <= '0';
            interrupt            <= '0';
            trap_interrupt       <= '1';
            execOPC              <= '0';
            getbrief             <= '0';
            TG68_PC_dec          <= "00";
            directPC             <= '0';
            directSR             <= '0';
            directCCR            <= '0';
            stop                 <= '0';
            exec_ADD             <= '0';
            exec_OR              <= '0';
            exec_AND             <= '0';
            exec_EOR             <= '0';
            exec_MOVE            <= '0';
            exec_MOVEQ           <= '0';
            exec_MOVESR          <= '0';
            exec_ADDQ            <= '0';
            exec_CMP             <= '0';
            exec_ROT             <= '0';
            exec_tas             <= '0'; -- RWK was missing
            exec_EXT             <= '0';
            exec_ABCD            <= '0';
            exec_SBCD            <= '0';
            exec_MULU            <= '0';
            exec_DIVU            <= '0';
            exec_Scc             <= '0';
            exec_CPMAW           <= '0';
            mem_byte             <= '0';
            rot_cnt              <= "000001";
            rot_nop              <= '0';
            get_extendedOPC      <= '0';
            get_bitnumber        <= '0';
            get_movem_mask       <= '0';
            test_maskzero        <= '0';
            movepl               <= '0';
            movepw               <= '0';
            test_delay           <= "000";
            PCmarker             <= '0';
        elsif rising_edge(clk) then
            if clkena_in = '1' then
                get_extendedOPC <= set_get_extendedOPC;
                get_bitnumber   <= set_get_bitnumber;
                get_movem_mask  <= set_get_movem_mask;
                test_maskzero   <= get_movem_mask;
                setstate_delay  <= setstate;

                TG68_PC_dec <= TG68_PC_dec(0)&set_TG68_PC_dec;
                if directPC = '1' and clkena = '1' then
                    TG68_PC <= data_read;
                elsif ea_to_pc = '1' and longread = '0' then
                    TG68_PC <= memaddr_in;
                elsif (state = "00" and TG68_PC_nop = '0') or TG68_PC_br8 = '1' or TG68_PC_brw = '1' or TG68_PC_dec(1) = '1' then
                    TG68_PC <= TG68_PC_add;
                end if;

                if get_bitnumber = '1' then
                    bit_number_reg <= data_read(4 downto 0);
                end if;

                if clkena = '1' or get_extendedOPC = '1' then
                    if set_get_extendedOPC = '1' then
                        state <= "00";
                    elsif get_extendedOPC = '1' then
                        state <= setstate_mux;
                    elsif fetchOPC = '1' or (state = "10" and write_back = '1' and setstate /= "10") or set_rot_cnt /= "000001" or stop = '1' then
                        state <= "01";  --decode cycle, execute cycle
                    else
                        state <= setstate_mux;
                    end if;
                    if setstate_mux(1) = '1' and datatype = "00" and set_get_extendedOPC = '0' and wait_mem_byte = '0' then
                        mem_byte <= '1';
                    else
                        mem_byte <= '0';
                    end if;

                end if;
            end if;

            if clkena = '1' then
                exec_ADD    <= '0';
                exec_OR     <= '0';
                exec_AND    <= '0';
                exec_EOR    <= '0';
                exec_MOVE   <= '0';
                exec_MOVEQ  <= '0';
                exec_MOVESR <= '0';
                exec_ADDQ   <= '0';
                exec_CMP    <= '0';
                exec_ROT    <= '0';
                exec_ABCD   <= '0';
                exec_SBCD   <= '0';
                fetchOPC    <= '0';
                exec_CPMAW  <= '0';
                endOPC      <= '0';
                interrupt   <= '0';
                execOPC     <= '0';
                exec_EXT    <= '0';
                exec_Scc    <= '0';
                rot_nop     <= '0';
                decodeOPC   <= fetchOPC;
                directPC    <= set_directPC;
                directSR    <= set_directSR;
                directCCR   <= set_directCCR;
                exec_MULU   <= set_exec_MULU;
                exec_DIVU   <= set_exec_DIVU;
                movepl      <= '0';
                movepw      <= '0';

                stop <= set_stop or (stop and not interrupt);
                if set_PCmarker = '1' then
                    PCmarker <= '1';
                elsif (state = "10" and longread = '0') or (ea_only = '1' and get_ea_now = '1') then
                    PCmarker <= '0';
                end if;
                if (decodeOPC or execOPC) = '1' then
                    rot_cnt <= set_rot_cnt;
                end if;
                if next_micro_state = idle and setstate_mux = "00" and (setnextpass = '0' or ea_only = '1') and endOPC = '0' and movem_busy = '0' and set_movem_busy = '0' and set_get_bitnumber = '0' then
                    nextpass <= '0';
                    if (exec_write_back = '0' or state = "11") and set_rot_cnt = "000001" then
                        endOPC <= '1';
                        if Flags(10 downto 8) < IPL_nr or IPL_nr = "111" then
                            interrupt <= '1';
                            rIPL_nr   <= IPL_nr;
                        else
                            if stop = '0' then
                                fetchOPC <= '1';
                            end if;
                        end if;
                    end if;
                    if exec_write_back = '0' or state /= "11" then
                        if stop = '0' then
                            execOPC <= '1';
                        end if;
                        exec_ADD    <= set_exec_ADD;
                        exec_OR     <= set_exec_OR;
                        exec_AND    <= set_exec_AND;
                        exec_EOR    <= set_exec_EOR;
                        exec_MOVE   <= set_exec_MOVE;
                        exec_MOVEQ  <= set_exec_MOVEQ;
                        exec_MOVESR <= set_exec_MOVESR;
                        exec_ADDQ   <= set_exec_ADDQ;
                        exec_CMP    <= set_exec_CMP;
                        exec_ROT    <= set_exec_ROT;
                        exec_tas    <= set_exec_tas;
                        exec_EXT    <= set_exec_EXT;
                        exec_ABCD   <= set_exec_ABCD;
                        exec_SBCD   <= set_exec_SBCD;
                        exec_Scc    <= set_exec_Scc;
                        exec_CPMAW  <= set_exec_CPMAW;
                        rot_nop     <= set_rot_nop;

                    end if;
                else
                    if endOPC = '0' and (setnextpass = '1' or (regdirectsource = '1' and decodeOPC = '1')) then
                        nextpass <= '1';
                    end if;
                end if;
                if interrupt = '1' then
                    opcode(15 downto 12) <= X"7";   --moveq
                    opcode(8 downto 6)   <= "010";  --long
--                                      trap_PC <= TG68_PC;
                    trap_interrupt       <= '1';
                end if;
                if fetchOPC = '1' then
                    trap_interrupt <= '0';
                    if (test_IPL = '1' and (Flags(10 downto 8) < IPL_nr or IPL_nr = "111")) or to_SR = '1' then
--                                      IF (test_IPL='1' AND (Flags(10 downto 8)<IPL_nr OR IPL_nr="111")) OR to_SR='1' OR opcode(15 downto 6)="0100111011" THEN  --nur f�r Validator
                        opcode <= X"60FE";
                        if to_SR = '0' then
                            test_delay <= "001";
                        end if;
                    else
                        opcode <= data_read(15 downto 0);
                    end if;
                    getbrief <= '0';
--                                      trap_PC <= TG68_PC;
                else
                    test_delay <= test_delay(1 downto 0)&'0';
                    getbrief   <= setgetbrief;
                    movepl     <= set_movepl;
                    movepw     <= set_movepw;
                end if;
                if decodeOPC = '1' or interrupt = '1' then
                    trap_SR <= Flags;
                end if;

                if getbrief = '1' then
                    brief <= data_read(15 downto 0);
                end if;
            end if;
        end if;
    end process;

-----------------------------------------------------------------------------
-- handle EA_data, data_write_tmp
-----------------------------------------------------------------------------
    process (clk, reset, opcode)
    begin
        if reset = '0' then
            set_store_in_tmp <= '0';
            exec_DIRECT      <= '0';
            exec_write_back  <= '0';
            direct_data      <= '0';
            use_direct_data  <= '0';
            Z_error          <= '0';
            data_write_tmp   <= (others => '0'); -- RWK adding default value at
                                                 -- reset
        elsif rising_edge(clk) then
            if clkena = '1' then
                direct_data <= '0';
                if endOPC = '1' then
                    set_store_in_tmp <= '0';
                    exec_DIRECT      <= '0';
                    exec_write_back  <= '0';
                    use_direct_data  <= '0';
                    Z_error          <= '0';
                else
                    if set_Z_error = '1' then
                        Z_error <= '1';
                    end if;
                    exec_DIRECT <= set_exec_MOVE;
                    if setstate_mux = "10" and write_back = '1' then
                        exec_write_back <= '1';
                    end if;
                end if;
                if set_direct_data = '1' then
                    direct_data     <= '1';
                    use_direct_data <= '1';
                end if;
                if set_exec_MOVE = '1' and state = "11" then
                    use_direct_data <= '1';
                end if;

                if (exec_DIRECT = '1' and state = "00" and getbrief = '0' and endOPC = '0') or state = "10" then
                    set_store_in_tmp <= '1';
                    ea_data          <= data_read;
                end if;

                if writePC_add = '1' then
                    data_write_tmp <= TG68_PC_add;
                elsif writePC = '1' or fetchOPC = '1' or interrupt = '1' or (trap_trap = '1' and decodeOPC = '1') then  --fetchOPC f�r Trap
                    data_write_tmp <= TG68_PC;
                elsif execOPC = '1' or (get_ea_now = '1' and ea_only = '1') then  --get_ea_now='1' AND ea_only='1' ist f�r pea
                    data_write_tmp <= registerin(31 downto 8)&(registerin(7)or exec_tas)&registerin(6 downto 0);
                elsif (exec_DIRECT = '1' and state = "10") or direct_data = '1' then
                    data_write_tmp <= data_read;
                    if movepl = '1' then
                        data_write_tmp(31 downto 8) <= data_write_tmp(23 downto 0);
                    end if;
                elsif (movem_busy = '1' and datatype = "10" and movem_presub = '1') or movepl = '1' then
                    data_write_tmp <= OP2out(15 downto 0)&OP2out(31 downto 16);
                elsif (not trapmake and decodeOPC) = '1' or movem_busy = '1' or movepw = '1' then
                    data_write_tmp <= OP2out;
                elsif writeSR = '1'then
                    data_write_tmp(15 downto 0) <= trap_SR(15 downto 8)& Flags(7 downto 0);
                end if;
            end if;
        end if;
    end process;

-----------------------------------------------------------------------------
-- set dest regaddr
-----------------------------------------------------------------------------
    process (opcode, rf_dest_addr_tmp, to_USP, Flags, trapmake, movem_addr, movem_presub, movem_regaddr, setbriefext, brief, setstackaddr, dest_hbits, dest_areg, data_is_source)
    begin
        rf_dest_addr <= rf_dest_addr_tmp;
        if rf_dest_addr_tmp(3 downto 0) = "1111" and to_USP = '0' then
            rf_dest_addr(4) <= Flags(13) or trapmake;
        end if;
        if movem_addr = '1' then
            if movem_presub = '1' then
                rf_dest_addr_tmp <= "000"&(movem_regaddr xor "1111");
            else
                rf_dest_addr_tmp <= "000"&movem_regaddr;
            end if;
        elsif setbriefext = '1' then
            rf_dest_addr_tmp <= ("000"&brief(15 downto 12));
        elsif setstackaddr = '1' then
            rf_dest_addr_tmp <= "0001111";
        elsif dest_hbits = '1' then
            rf_dest_addr_tmp <= "000"&dest_areg&opcode(11 downto 9);
        else
            if opcode(5 downto 3) = "000" or data_is_source = '1' then
                rf_dest_addr_tmp <= "000"&dest_areg&opcode(2 downto 0);
            else
                rf_dest_addr_tmp <= "0001"&opcode(2 downto 0);
            end if;
        end if;
    end process;

-----------------------------------------------------------------------------
-- set OP1
-----------------------------------------------------------------------------
    process (reg_QA, OP1out_zero, from_SR, Flags, ea_data_OP1, set_store_in_tmp, ea_data)
    begin
        OP1out <= reg_QA;
        if OP1out_zero = '1' then
            OP1out <= (others => '0');
        elsif from_SR = '1' then
            OP1out(15 downto 0) <= Flags;
        elsif ea_data_OP1 = '1' and set_store_in_tmp = '1' then
            OP1out <= ea_data;
        end if;
    end process;

-----------------------------------------------------------------------------
-- set source regaddr
-----------------------------------------------------------------------------
    process (opcode, Flags, movem_addr, movem_presub, movem_regaddr, source_lowbits, source_areg, from_USP, rf_source_addr_tmp)
    begin
        rf_source_addr <= rf_source_addr_tmp;
        if rf_source_addr_tmp(3 downto 0) = "1111" and from_USP = '0' then
            rf_source_addr(4) <= Flags(13);
        end if;
        if movem_addr = '1' then
            if movem_presub = '1' then
                rf_source_addr_tmp <= "000"&(movem_regaddr xor "1111");
            else
                rf_source_addr_tmp <= "000"&movem_regaddr;
            end if;
        elsif from_USP = '1' then
            rf_source_addr_tmp <= "0001111";
        elsif source_lowbits = '1' then
            rf_source_addr_tmp <= "000"&source_areg&opcode(2 downto 0);
        else
            rf_source_addr_tmp <= "000"&source_areg&opcode(11 downto 9);
        end if;
    end process;

-----------------------------------------------------------------------------
-- set OP2
-----------------------------------------------------------------------------
    process (OP2out, reg_QB, opcode, datatype, OP2out_one, exec_EXT, exec_MOVEQ, EXEC_ADDQ, use_direct_data, data_write_tmp,
             ea_data_OP1, set_store_in_tmp, ea_data, movepl)
    begin
        OP2out(15 downto 0)  <= reg_QB(15 downto 0);
        OP2out(31 downto 16) <= (others => OP2out(15));
        if OP2out_one = '1' then
            OP2out(15 downto 0) <= "1111111111111111";
        elsif exec_EXT = '1' then
            if opcode(6) = '0' then     --ext.w
                OP2out(15 downto 8) <= (others => OP2out(7));
            end if;
        elsif use_direct_data = '1' then
            OP2out <= data_write_tmp;
        elsif ea_data_OP1 = '0' and set_store_in_tmp = '1' then
            OP2out <= ea_data;
        elsif exec_MOVEQ = '1' then
            OP2out(7 downto 0)  <= opcode(7 downto 0);
            OP2out(15 downto 8) <= (others => opcode(7));
        elsif exec_ADDQ = '1' then
            OP2out(2 downto 0) <= opcode(11 downto 9);
            if opcode(11 downto 9) = "000" then
                OP2out(3) <= '1';
            else
                OP2out(3) <= '0';
            end if;
            OP2out(15 downto 4) <= (others => '0');
        elsif datatype = "10" or movepl = '1' then
            OP2out(31 downto 16) <= reg_QB(31 downto 16);
        end if;
    end process;

-----------------------------------------------------------------------------
-- addsub
-----------------------------------------------------------------------------
    process (OP1out, OP2out, presub, postadd, execOPC, OP2out_one, datatype, use_SP, use_XZFlag, use_XFlag, Flags, setaddsub)
    begin
        addsub_a <= OP1out;
        addsub_b <= OP2out;
        addsub   <= not presub;
        c_in(0)  <= '0';
        if execOPC = '0' and OP2out_one = '0' then
            if datatype = "00" and use_SP = '0' then
                addsub_b <= "00000000000000000000000000000001";
            elsif datatype = "10" and (presub or postadd) = '1' then
                addsub_b <= "00000000000000000000000000000100";
            else
                addsub_b <= "00000000000000000000000000000010";
            end if;
        else
            if (use_XZFlag = '1' or use_XFlag = '1') and Flags(4) = '1' then
                c_in(0) <= '1';
            end if;
            addsub <= setaddsub;
        end if;
    end process;

-----------------------------------------------------------------------------
-- Write Reg
-----------------------------------------------------------------------------
    process (clkena, OP1in, datatype, presub, postadd, endOPC, regwrena, state, execOPC, last_data_read, movem_addr, rf_dest_addr, reg_QA, maskzero)
    begin
        Lwrena     <= '0';
        Hwrena     <= '0';
        registerin <= OP1in;

        if (presub = '1' or postadd = '1') and endOPC = '0' then  -- -(An)+
            Hwrena <= '1';
            Lwrena <= '1';
        elsif Regwrena = '1' and maskzero = '0' then              --read (mem)
            Lwrena <= '1';
            case datatype is
                when "00" =>                                      --BYTE
                    registerin(15 downto 8) <= reg_QA(15 downto 8);
                when "01" =>                                      --WORD
                    if rf_dest_addr(3) = '1' or movem_addr = '1' then
                        Hwrena <= '1';
                    end if;
                when others =>                                    --LONG
                    Hwrena <= '1';
            end case;
        end if;
    end process;

------------------------------------------------------------------------------
--ALU
------------------------------------------------------------------------------
    process (opcode, OP1in, OP1out, OP2out, datatype, c_out, exec_ABCD, exec_SBCD, exec_CPMAW, exec_MOVESR, bits_out, Flags, flag_z, use_XZFlag, addsub_ofl,
             dummy_s, dummy_a, niba_hc, niba_h, niba_l, niba_lc, nibs_hc, nibs_h, nibs_l, nibs_lc, addsub_q, movem_addr, data_read, exec_MULU, exec_DIVU, exec_OR,
             exec_AND, exec_Scc, exec_EOR, exec_MOVE, exec_exg, exec_ROT, execOPC, exec_swap, exec_Bits, rot_out, dummy_mulu, dummy_div, save_memaddr, memaddr,
             memaddr_in, ea_only, get_ea_now)
    begin

--BCD_ARITH-------------------------------------------------------------------
        --ADC
        dummy_a <= niba_hc&(niba_h(4 downto 1)+('0', niba_hc, niba_hc, '0'))&(niba_l(4 downto 1)+('0', niba_lc, niba_lc, '0'));
        niba_l  <= ('0'&OP1out(3 downto 0)&'1') + ('0'&OP2out(3 downto 0)&Flags(4));
        niba_lc <= niba_l(5) or (niba_l(4) and niba_l(3)) or (niba_l(4) and niba_l(2));

        niba_h  <= ('0'&OP1out(7 downto 4)&'1') + ('0'&OP2out(7 downto 4)&niba_lc);
        niba_hc <= niba_h(5) or (niba_h(4) and niba_h(3)) or (niba_h(4) and niba_h(2));
        --SBC
        dummy_s <= nibs_hc&(nibs_h(4 downto 1)-('0', nibs_hc, nibs_hc, '0'))&(nibs_l(4 downto 1)-('0', nibs_lc, nibs_lc, '0'));
        nibs_l  <= ('0'&OP1out(3 downto 0)&'0') - ('0'&OP2out(3 downto 0)&Flags(4));
        nibs_lc <= nibs_l(5);

        nibs_h  <= ('0'&OP1out(7 downto 4)&'0') - ('0'&OP2out(7 downto 4)&nibs_lc);
        nibs_hc <= nibs_h(5);
------------------------------------------------------------------------------

        flag_z <= "000";

        OP1in <= addsub_q;
        if movem_addr = '1' then
            OP1in <= data_read;
        elsif exec_ABCD = '1' then
            OP1in(7 downto 0) <= dummy_a(7 downto 0);
        elsif exec_SBCD = '1' then
            OP1in(7 downto 0) <= dummy_s(7 downto 0);
        elsif exec_MULU = '1' then
            OP1in <= dummy_mulu;
        elsif exec_DIVU = '1' and execOPC = '1' then
            OP1in <= dummy_div;
        elsif exec_OR = '1' then
            OP1in <= OP2out or OP1out;
        elsif exec_AND = '1' or exec_Scc = '1' then
            OP1in <= OP2out and OP1out;
        elsif exec_EOR = '1' then
            OP1in <= OP2out xor OP1out;
        elsif exec_MOVE = '1' or exec_exg = '1' then
            OP1in <= OP2out;
        elsif exec_ROT = '1' then
            OP1in <= rot_out;
        elsif save_memaddr = '1' then
            OP1in <= memaddr;
        elsif get_ea_now = '1' and ea_only = '1' then
            OP1in <= memaddr_in;
        elsif exec_swap = '1' then
            OP1in <= OP1out(15 downto 0)& OP1out(31 downto 16);
        elsif exec_bits = '1' then
            OP1in <= bits_out;
        elsif exec_MOVESR = '1' then
            OP1in(15 downto 0) <= Flags;
        end if;

        if use_XZFlag = '1' and flags(2) = '0' then
            flag_z <= "000";
        elsif OP1in(7 downto 0) = "00000000" then
            flag_z(0) <= '1';
            if OP1in(15 downto 8) = "00000000" then
                flag_z(1) <= '1';
                if OP1in(31 downto 16) = "0000000000000000" then
                    flag_z(2) <= '1';
                end if;
            end if;
        end if;

--                                      --Flags NZVC
        if datatype = "00" then                         --Byte
            set_flags <= OP1IN(7)&flag_z(0)&addsub_ofl(0)&c_out(0);
            if exec_ABCD = '1' then
                set_flags(0) <= dummy_a(8);
            elsif exec_SBCD = '1' then
                set_flags(0) <= dummy_s(8);
            end if;
        elsif datatype = "10" or exec_CPMAW = '1' then  --Long
            set_flags <= OP1IN(31)&flag_z(2)&addsub_ofl(2)&c_out(2);
        else                                            --Word
            set_flags <= OP1IN(15)&flag_z(1)&addsub_ofl(1)&c_out(1);
        end if;
    end process;

------------------------------------------------------------------------------
--Flags
------------------------------------------------------------------------------
    process (clk, reset, opcode)
    begin
        if reset = '0' then
            Flags(13)          <= '1';
            SVmode             <= '1';
            Flags(10 downto 8) <= "111";
            --Flags(7 downto 0)  <= (others => '0'); -- RWK for clean start
        elsif rising_edge(clk) then

            if clkena = '1' then
                if directSR = '1' then
                    Flags <= data_read(15 downto 0);
                end if;
                if directCCR = '1' then
                    Flags(7 downto 0) <= data_read(7 downto 0);
                end if;
                if interrupt = '1' then
                    Flags(10 downto 8) <= rIPL_nr;
                    SVmode             <= '1';
                end if;
                if writeSR = '1' or interrupt = '1' then
                    Flags(13) <= '1';
                end if;
                if endOPC = '1' and to_SR = '0' then
                    SVmode <= Flags(13);
                end if;
                if execOPC = '1' and to_SR = '1' then
                    Flags(7 downto 0) <= OP1in(7 downto 0);        --CCR
                    if datatype = "01" and (opcode(14) = '0' or opcode(9) = '1') then  --move to CCR wird als word gespeichert
                        Flags(15 downto 8) <= OP1in(15 downto 8);  --SR
                        SVmode             <= OP1in(13);
                    end if;
                elsif Z_error = '1' then
                    if opcode(8) = '0' then
                        Flags(3 downto 0) <= "1000";
                    else
                        Flags(3 downto 0) <= "0100";
                    end if;
                elsif no_Flags = '0' and trapmake = '0' then
                    if exec_ADD = '1' then
                        Flags(4) <= set_flags(0);
                    elsif exec_ROT = '1' and rot_bits /= "11" and rot_nop = '0' then
                        Flags(4) <= rot_XC;
                    end if;

                    if (exec_ADD or exec_CMP) = '1' then
                        Flags(3 downto 0) <= set_flags;
                    elsif decodeOPC = '1' and set_exec_ROT = '1' then
                        Flags(1) <= '0';
                    elsif exec_DIVU = '1' then
                        if set_V_Flag = '1' then
                            Flags(3 downto 0) <= "1010";
                        else
                            Flags(3 downto 0) <= OP1IN(15)&flag_z(1)&"00";
                        end if;
                    elsif exec_OR = '1' or exec_AND = '1' or exec_EOR = '1' or exec_MOVE = '1' or exec_swap = '1' or exec_MULU = '1' then
                        Flags(3 downto 0) <= set_flags(3 downto 2)&"00";
                    elsif exec_ROT = '1' then
                        Flags(3 downto 2) <= set_flags(3 downto 2);
                        Flags(0)          <= rot_XC;
                        if rot_bits = "00" then  --ASL/ASR
                            Flags(1) <= ((set_flags(3) xor rot_rot) or Flags(1));
                        end if;
                    elsif exec_bits = '1' then
                        Flags(2) <= not one_bit_in;
                    end if;
                end if;
            end if;
        end if;
    end process;

-----------------------------------------------------------------------------
-- execute opcode
-----------------------------------------------------------------------------
    process (clk, reset, OP2out, opcode, fetchOPC, decodeOPC, execOPC, endOPC, nextpass, condition, set_V_flag, trapmake, trapd, interrupt, trap_interrupt, rot_nop,
             Z_error, c_in, rot_cnt, one_bit_in, bit_number_reg, bit_number, ea_only, get_ea_now, ea_build, datatype, exec_write_back, get_extendedOPC,
             Flags, SVmode, movem_addr, movem_busy, getbrief, set_exec_AND, set_exec_OR, set_exec_EOR, TG68_PC_dec, c_out, OP1out, micro_state)
    begin
        TG68_PC_br8         <= '0';
        TG68_PC_brw         <= '0';
        TG68_PC_nop         <= '0';
        setstate            <= "00";
        Regwrena            <= '0';
        postadd             <= '0';
        presub              <= '0';
        movem_presub        <= '0';
        setaddsub           <= '1';
        setaddrlong         <= '0';
        setnextpass         <= '0';
        regdirectsource     <= '0';
        setdisp             <= '0';
        setdispbyte         <= '0';
        setdispbrief        <= '0';
        setbriefext         <= '0';
        setgetbrief         <= '0';
        longreaddirect      <= '0';
        dest_areg           <= '0';
        source_areg         <= '0';
        data_is_source      <= '0';
        write_back          <= '0';
        setstackaddr        <= '0';
        writePC             <= '0';
        writePC_add         <= '0';
        set_TG68_PC_dec     <= '0';
        set_directPC        <= '0';
        set_exec_ADD        <= '0';
        set_exec_OR         <= '0';
        set_exec_AND        <= '0';
        set_exec_EOR        <= '0';
        set_exec_MOVE       <= '0';
        set_exec_MOVEQ      <= '0';
        set_exec_MOVESR     <= '0';
        set_exec_ADDQ       <= '0';
        set_exec_CMP        <= '0';
        set_exec_ROT        <= '0';
        set_exec_EXT        <= '0';
        set_exec_CPMAW      <= '0';
        OP2out_one          <= '0';
        ea_to_pc            <= '0';
        ea_build            <= '0';
        get_ea_now          <= '0';
        rot_bits            <= "XX";
        set_rot_nop         <= '0';
        set_rot_cnt         <= "000001";
        set_movem_busy      <= '0';
        set_get_movem_mask  <= '0';
        save_memaddr        <= '0';
        set_mem_addsub      <= '0';
        exec_exg            <= '0';
        exec_swap           <= '0';
        exec_Bits           <= '0';
        set_get_bitnumber   <= '0';
        dest_hbits          <= '0';
        source_lowbits      <= '0';
        set_mem_rega        <= '0';
        ea_data_OP1         <= '0';
        ea_only             <= '0';
        set_direct_data     <= '0';
        set_get_extendedOPC <= '0';
        set_exec_tas        <= '0';
        OP1out_zero         <= '0';
        use_XZFlag          <= '0';
        use_XFlag           <= '0';
        set_exec_ABCD       <= '0';
        set_exec_SBCD       <= '0';
        set_exec_MULU       <= '0';
        set_exec_DIVU       <= '0';
        set_exec_Scc        <= '0';
        trap_illegal        <= '0';
        trap_priv           <= '0';
        trap_1010           <= '0';
        trap_1111           <= '0';
        trap_trap           <= '0';
        trap_trapv          <= '0';
        trapmake            <= '0';
        set_vectoraddr      <= '0';
        writeSR             <= '0';
        set_directSR        <= '0';
        set_directCCR       <= '0';
        set_stop            <= '0';
        from_SR             <= '0';
        to_SR               <= '0';
        from_USP            <= '0';
        to_USP              <= '0';
        illegal_write_mode  <= '0';
        illegal_read_mode   <= '0';
        illegal_byteaddr    <= '0';
        no_Flags            <= '0';
        set_PCmarker        <= '0';
        use_SP              <= '0';
        set_Z_error         <= '0';
        wait_mem_byte       <= '0';
        set_movepl          <= '0';
        set_movepw          <= '0';

        trap_chk         <= '0';
        next_micro_state <= idle;

------------------------------------------------------------------------------
--Sourcepass
------------------------------------------------------------------------------
        if ea_only = '0' and get_ea_now = '1' then
            setstate <= "10";
        end if;

        if ea_build = '1' then
            case opcode(5 downto 3) is        --source
                when "010"|"011"|"100" =>     -- -(An)+
                    get_ea_now  <= '1';
                    setnextpass <= '1';
                    if opcode(4) = '1' then
                        set_mem_rega <= '1';
                    else
                        set_mem_addsub <= '1';
                    end if;
                    if opcode(3) = '1' then   --(An)+
                        postadd <= '1';
                        if opcode(2 downto 0) = "111" then
                            use_SP <= '1';
                        end if;
                    end if;
                    if opcode(5) = '1' then   -- -(An)
                        presub <= '1';
                        if opcode(2 downto 0) = "111" then
                            use_SP <= '1';
                        end if;
                    end if;
                    if opcode(4 downto 3) /= "10" then
                        regwrena <= '1';
                    end if;
                when "101" =>                 --(d16,An)
                    next_micro_state <= ld_dAn1;
                    setgetbrief      <= '1';
                    set_mem_regA     <= '1';
                when "110" =>                 --(d8,An,Xn)
                    next_micro_state <= ld_AnXn1;
                    setgetbrief      <= '1';
                    set_mem_regA     <= '1';
                when "111" =>
                    case opcode(2 downto 0) is
                        when "000" =>         --(xxxx).w
                            next_micro_state <= ld_nn;
                        when "001" =>         --(xxxx).l
                            longreaddirect   <= '1';
                            next_micro_state <= ld_nn;
                        when "010" =>         --(d16,PC)
                            next_micro_state <= ld_dAn1;
                            setgetbrief      <= '1';
                            set_PCmarker     <= '1';
                        when "011" =>         --(d8,PC,Xn)
                            next_micro_state <= ld_AnXn1;
                            setgetbrief      <= '1';
                            set_PCmarker     <= '1';
                        when "100" =>         --#data
                            setnextpass     <= '1';
                            set_direct_data <= '1';
                            if datatype = "10" then
                                longreaddirect <= '1';
                            end if;
                        when others =>
                    end case;
                when others =>
            end case;
        end if;
------------------------------------------------------------------------------
--prepere opcode
------------------------------------------------------------------------------
        case opcode(7 downto 6) is
            when "00"   => datatype <= "00";  --Byte
            when "01"   => datatype <= "01";  --Word
            when others => datatype <= "10";  --Long
        end case;

        if execOPC = '1' and endOPC = '0' and exec_write_back = '1' then
            setstate <= "11";
        end if;

------------------------------------------------------------------------------
--test illegal mode
------------------------------------------------------------------------------
        if (opcode(5 downto 3) = "111" and opcode(2 downto 1) /= "00") or (opcode(5 downto 3) = "001" and datatype = "00") then
            illegal_write_mode <= '1';
        end if;
        if (opcode(5 downto 2) = "1111" and opcode(1 downto 0) /= "00") or (opcode(5 downto 3) = "001" and datatype = "00") then
            illegal_read_mode <= '1';
        end if;
        if opcode(5 downto 3) = "001" and datatype = "00" then
            illegal_byteaddr <= '1';
        end if;


        case opcode(15 downto 12) is
-- 0000 ----------------------------------------------------------------------------
            when "0000" =>
                if opcode(8) = '1' and opcode(5 downto 3) = "001" then  --movep
                    datatype <= "00";   --Byte
                    use_SP   <= '1';
                    no_Flags <= '1';
                    if opcode(7) = '0' then
                        set_exec_move <= '1';
                        set_movepl    <= '1';
                    end if;
                    if decodeOPC = '1' then
                        if opcode(7) = '0' then
                            set_direct_data <= '1';
                        end if;
                        next_micro_state <= movep1;
                        setgetbrief      <= '1';
                        set_mem_regA     <= '1';
                    end if;
                    if opcode(7) = '0' and endOPC = '1' then
                        if opcode(6) = '1' then
                            datatype <= "10";                       --Long
                        else
                            datatype <= "01";                       --Word
                        end if;
                        dest_hbits <= '1';
                        regwrena   <= '1';
                    end if;
                else
                    if opcode(8) = '1' or opcode(11 downto 8) = "1000" then  --Bits
                        if execOPC = '1' and get_extendedOPC = '0' then
                            if opcode(7 downto 6) /= "00" and endOPC = '1' then
                                regwrena <= '1';
                            end if;
                            exec_Bits   <= '1';
                            ea_data_OP1 <= '1';
                        end if;
--                                      IF get_extendedOPC='1' THEN
--                                              datatype <= "01";                       --Word
--                                      ELS
                        if opcode(5 downto 4) = "00" then
                            datatype <= "10";                       --Long
                        else
                            datatype <= "00";                       --Byte
                            if opcode(7 downto 6) /= "00" then
                                write_back <= '1';
                            end if;
                        end if;
                        if decodeOPC = '1' then
                            ea_build <= '1';
                            if opcode(8) = '0' then
                                if opcode(5 downto 4) /= "00" then  --Dn, An
                                    set_get_extendedOPC <= '1';
                                end if;
                                set_get_bitnumber <= '1';
                            end if;
                        end if;
                    else                --andi, ...xxxi
                        if opcode(11 downto 8) = "0000" then        --ORI
                            set_exec_OR <= '1';
                        end if;
                        if opcode(11 downto 8) = "0010" then        --ANDI
                            set_exec_AND <= '1';
                        end if;
                        if opcode(11 downto 8) = "0100" or opcode(11 downto 8) = "0110" then  --SUBI, ADDI
                            set_exec_ADD <= '1';
                        end if;
                        if opcode(11 downto 8) = "1010" then        --EORI
                            set_exec_EOR <= '1';
                        end if;
                        if opcode(11 downto 8) = "1100" then        --CMPI
                            set_exec_CMP <= '1';
                        elsif trapmake = '0' then
                            write_back <= '1';
                        end if;
                        if opcode(7) = '0' and opcode(5 downto 0) = "111100" and (set_exec_AND or set_exec_OR or set_exec_EOR) = '1' then  --SR
--                                      IF opcode(7)='0' AND opcode(5 downto 0)="111100" AND (opcode(11 downto 8)="0010" OR opcode(11 downto 8)="0000" OR opcode(11 downto 8)="1010") THEN              --SR
                            if SVmode = '0' and opcode(6) = '1' then    --SR
                                trap_priv <= '1';
                                trapmake  <= '1';
                            else
                                from_SR <= '1';
                                to_SR   <= '1';
                                if decodeOPC = '1' then
                                    setnextpass     <= '1';
                                    set_direct_data <= '1';
                                end if;
                            end if;
                        else
                            if decodeOPC = '1' then
                                if opcode(11 downto 8) = "0010" or opcode(11 downto 8) = "0000" or opcode(11 downto 8) = "0100"  --ANDI, ORI, SUBI
                                    or opcode(11 downto 8) = "0110" or opcode(11 downto 8) = "1010" or opcode(11 downto 8) = "1100" then  --ADDI, EORI, CMPI
                                    --                                          IF (set_exec_AND OR set_exec_OR OR set_exec_ADD         --ANDI, ORI, SUBI
                                    --                                             OR set_exec_EOR OR set_exec_CMP)='1' THEN    --ADDI, EORI, CMPI

                                    next_micro_state <= andi;
                                    set_direct_data  <= '1';
                                    if datatype = "10" then
                                        longreaddirect <= '1';
                                    end if;
                                end if;
                            end if;

                            if execOPC = '1' then
                                ea_data_OP1 <= '1';
                                if opcode(11 downto 8) /= "1100" then  --CMPI
                                    if endOPC = '1' then
                                        Regwrena <= '1';
                                    end if;
                                end if;
                                if opcode(11 downto 8) = "1100" or opcode(11 downto 8) = "0100" then  --CMPI, SUBI
                                    setaddsub <= '0';
                                end if;
                            end if;
                        end if;
                    end if;
                end if;

-- 0001, 0010, 0011 -----------------------------------------------------------------
            when "0001"|"0010"|"0011" =>  --move.b, move.l, move.w
                set_exec_MOVE <= '1';
                if opcode(8 downto 6) = "001" then
                    no_Flags <= '1';
                end if;
                if opcode(5 downto 4) = "00" then          --Dn, An
                    regdirectsource <= '1';
                end if;
                case opcode(13 downto 12) is
                    when "01"   => datatype <= "00";       --Byte
                    when "10"   => datatype <= "10";       --Long
                    when others => datatype <= "01";       --Word
                end case;
                source_lowbits <= '1';  -- Dn=>  An=>
                if opcode(3) = '1' then
                    source_areg <= '1';
                end if;
                if getbrief = '1' and nextpass = '1' then  -- =>(d16,An)  =>(d8,An,Xn)
                    set_mem_rega <= '1';
                end if;

                if execOPC = '1' and opcode(8 downto 7) = "00" then
                    Regwrena <= '1';
                end if;

                if nextpass = '1' or execOPC = '1' or opcode(5 downto 4) = "00" then
                    dest_hbits <= '1';
                    if opcode(8 downto 6) /= "000" then
                        dest_areg <= '1';
                    end if;
                end if;

                if decodeOPC = '1' then
                    ea_build <= '1';
                end if;

                if micro_state = idle and (nextpass = '1' or (opcode(5 downto 4) = "00" and decodeOPC = '1')) then
                    case opcode(8 downto 6) is           --destination
--                                              WHEN "000" =>                                           --Dn
--                                              WHEN "001" =>                                           --An
                        when "010"|"011"|"100" =>        --destination -(an)+
                            if opcode(7) = '1' then
                                set_mem_rega <= '1';
                            else
                                set_mem_addsub <= '1';
                            end if;
                            if opcode(6) = '1' then      --(An)+
                                postadd <= '1';
                                if opcode(11 downto 9) = "111" then
                                    use_SP <= '1';
                                end if;
                            end if;
                            if opcode(8) = '1' then      -- -(An)
                                presub <= '1';
                                if opcode(11 downto 9) = "111" then
                                    use_SP <= '1';
                                end if;
                            end if;
                            if opcode(7 downto 6) /= "10" then
                                regwrena <= '1';
                            end if;
                            setstate         <= "11";
                            next_micro_state <= nop;
                        when "101" =>   --(d16,An)
                            next_micro_state <= st_dAn1;
                            set_mem_regA     <= '1';
                            setgetbrief      <= '1';
                        when "110" =>   --(d8,An,Xn)
                            next_micro_state <= st_AnXn1;
                            set_mem_regA     <= '1';
                            setgetbrief      <= '1';
                        when "111" =>
                            case opcode(11 downto 9) is
                                when "000" =>            --(xxxx).w
                                    next_micro_state <= st_nn;
                                when "001" =>            --(xxxx).l
                                    longreaddirect   <= '1';
                                    next_micro_state <= st_nn;
                                when others =>
                            end case;
                        when others =>
                    end case;
                end if;
-- 0100 ----------------------------------------------------------------------------
            when "0100" =>              --rts_group
                if opcode(8) = '1' then                  --lea
                    if opcode(6) = '1' then              --lea
                        if opcode(7) = '1' then
                            ea_only <= '1';
                            if opcode(5 downto 3) = "010" then  --lea (Am),An
                                set_exec_move  <= '1';
                                no_Flags       <= '1';
                                dest_areg      <= '1';
                                dest_hbits     <= '1';
                                source_lowbits <= '1';
                                source_areg    <= '1';
                                if execOPC = '1' then
                                    Regwrena <= '1';
                                end if;
                            else
                                if decodeOPC = '1' then
                                    ea_build <= '1';
                                end if;
                            end if;
                            if get_ea_now = '1' then
                                dest_areg  <= '1';
                                dest_hbits <= '1';
                                regwrena   <= '1';
                            end if;
                        else
                            trap_illegal <= '1';
                            trapmake     <= '1';
                        end if;
                    else                --chk
                        if opcode(7) = '1' then
                            set_exec_ADD <= '1';
                            if decodeOPC = '1' then
                                ea_build <= '1';
                            end if;
                            datatype <= "01";            --Word
                            if execOPC = '1' then
                                setaddsub   <= '0';
--first alternative
                                ea_data_OP1 <= '1';
                                if c_out(1) = '1' or OP1out(15) = '1' or OP2out(15) = '1' then
                                                         --                              trap_chk <= '1';        --first I must change the Trap System
                                                         --                              trapmake <= '1';
                                end if;
--second alternative
--                                                              IF (c_out(1)='0' AND flag_z(1)='0') OR OP1out(15)='1' OR OP2out(15)='1' THEN
--                                      --                              trap_chk <= '1';        --first I must change the Trap System
--                                      --                              trapmake <= '1';
--                                                              END IF;
--                                                              dest_hbits <= '1';
--                                                              source_lowbits <='1';
                            end if;
                        else
                            trap_illegal <= '1';         -- chk long for 68020
                            trapmake     <= '1';
                        end if;
                    end if;
                else
                    case opcode(11 downto 9) is
                        when "000"=>
                            if decodeOPC = '1' then
                                ea_build <= '1';
                            end if;
                            if opcode(7 downto 6) = "11" then  --move from SR
                                set_exec_MOVESR <= '1';
                                datatype        <= "01";
                                write_back      <= '1';  -- im 68000 wird auch erst gelesen
                                if execOPC = '1' then
                                    if endOPC = '1' then
                                        Regwrena <= '1';
                                    end if;
                                end if;
                            else        --negx
                                use_XFlag    <= '1';
                                write_back   <= '1';
                                set_exec_ADD <= '1';
                                setaddsub    <= '0';
                                if execOPC = '1' then
                                    source_lowbits <= '1';
                                    OP1out_zero    <= '1';
                                    if endOPC = '1' then
                                        Regwrena <= '1';
                                    end if;
                                end if;
                            end if;
                        when "001"=>
                            if opcode(7 downto 6) = "11" then  --move from CCR 68010
                                trap_illegal <= '1';
                                trapmake     <= '1';
                            else        --clr
                                if decodeOPC = '1' then
                                    ea_build <= '1';
                                end if;
                                write_back   <= '1';
                                set_exec_AND <= '1';
                                if execOPC = '1' then
                                    OP1out_zero <= '1';
                                    if endOPC = '1' then
                                        Regwrena <= '1';
                                    end if;
                                end if;
                            end if;
                        when "010"=>
                            if decodeOPC = '1' then
                                ea_build <= '1';
                            end if;
                            if opcode(7 downto 6) = "11" then  --move to CCR
                                set_exec_MOVE <= '1';
                                datatype      <= "01";
                                if execOPC = '1' then
                                    source_lowbits <= '1';
                                    to_SR          <= '1';
                                end if;
                            else        --neg
                                write_back   <= '1';
                                set_exec_ADD <= '1';
                                setaddsub    <= '0';
                                if execOPC = '1' then
                                    source_lowbits <= '1';
                                    OP1out_zero    <= '1';
                                    if endOPC = '1' then
                                        Regwrena <= '1';
                                    end if;
                                end if;
                            end if;
                        when "011" =>   --not, move toSR
                            if opcode(7 downto 6) = "11" then  --move to SR
                                if SVmode = '1' then
                                    if decodeOPC = '1' then
                                        ea_build <= '1';
                                    end if;
                                    set_exec_MOVE <= '1';
                                    datatype      <= "01";
                                    if execOPC = '1' then
                                        source_lowbits <= '1';
                                        to_SR          <= '1';
                                    end if;
                                else
                                    trap_priv <= '1';
                                    trapmake  <= '1';
                                end if;
                            else        --not
                                if decodeOPC = '1' then
                                    ea_build <= '1';
                                end if;
                                write_back   <= '1';
                                set_exec_EOR <= '1';
                                if execOPC = '1' then
                                    OP2out_one  <= '1';
                                    ea_data_OP1 <= '1';
                                    if endOPC = '1' then
                                        Regwrena <= '1';
                                    end if;
                                end if;
                            end if;
                        when "100"|"110"=>
                            if opcode(7) = '1' then      --movem, ext
                                if opcode(5 downto 3) = "000" and opcode(10) = '0' then  --ext
                                    source_lowbits <= '1';
                                    if decodeOPC = '1' then
                                        set_exec_EXT  <= '1';
                                        set_exec_move <= '1';
                                    end if;
                                    if opcode(6) = '0' then
                                        datatype <= "01";      --WORD
                                    end if;
                                    if execOPC = '1' then
                                        regwrena <= '1';
                                    end if;
                                else    --movem
--                                                              IF opcode(11 downto 7)="10001" OR opcode(11 downto 7)="11001" THEN      --MOVEM
                                    ea_only <= '1';
                                    if decodeOPC = '1' then
                                        datatype            <= "01";  --Word
                                        set_get_movem_mask  <= '1';
                                        set_get_extendedOPC <= '1';

                                        if opcode(5 downto 3) = "010" or opcode(5 downto 3) = "011" or opcode(5 downto 3) = "100" then
                                            set_mem_rega <= '1';
                                            setstate     <= "01";
                                            if opcode(10) = '0' then
                                                set_movem_busy <= '1';
                                            else
                                                next_micro_state <= movem;
                                            end if;
                                        else
                                            ea_build <= '1';
                                        end if;

                                    else
                                        if opcode(6) = '0' then
                                            datatype <= "01";  --Word
                                        end if;
                                    end if;
                                    if execOPC = '1' then
                                        if opcode(5 downto 3) = "100" or opcode(5 downto 3) = "011" then
                                            regwrena     <= '1';
                                            save_memaddr <= '1';
                                        end if;
                                    end if;
                                    if get_ea_now = '1' then
                                        set_movem_busy <= '1';
                                        if opcode(10) = '0' then
                                            setstate <= "01";
                                        else
                                            setstate <= "10";
                                        end if;
                                    end if;
                                    if opcode(5 downto 3) = "100" then
                                        movem_presub <= '1';
                                    end if;
                                    if movem_addr = '1' then
                                        if opcode(10) = '1' then
                                            regwrena <= '1';
                                        end if;
                                    end if;
                                    if movem_busy = '1' then
                                        if opcode(10) = '0' then
                                            setstate <= "11";
                                        else
                                            setstate <= "10";
                                        end if;
                                    end if;
                                end if;
                            else
                                if opcode(10) = '1' then       --MUL, DIV 68020
                                    trap_illegal <= '1';
                                    trapmake     <= '1';
                                else    --pea, swap
                                    if opcode(6) = '1' then
                                        datatype <= "10";
                                        if opcode(5 downto 3) = "000" then  --swap
                                            if execOPC = '1' then
                                                exec_swap <= '1';
                                                regwrena  <= '1';
                                            end if;
                                        elsif opcode(5 downto 3) = "001" then  --bkpt

                                        else                     --pea
                                            ea_only <= '1';
                                            if decodeOPC = '1' then
                                                ea_build <= '1';
                                            end if;
                                            if nextpass = '1' and micro_state = idle then
                                                presub           <= '1';
                                                setstackaddr     <= '1';
                                                set_mem_addsub   <= '1';
                                                setstate         <= "11";
                                                next_micro_state <= nop;
                                            end if;
                                            if get_ea_now = '1' then
                                                setstate <= "01";
                                            end if;
                                        end if;
                                    else                         --nbcd
                                        if decodeOPC = '1' then  --nbcd
                                            ea_build <= '1';
                                        end if;
                                        use_XFlag     <= '1';
                                        write_back    <= '1';
                                        set_exec_ADD  <= '1';
                                        set_exec_SBCD <= '1';
                                        if execOPC = '1' then
                                            source_lowbits <= '1';
                                            OP1out_zero    <= '1';
                                            if endOPC = '1' then
                                                Regwrena <= '1';
                                            end if;
                                        end if;
                                    end if;
                                end if;
                            end if;

                        when "101" =>   --tst, tas
                            if opcode(7 downto 2) = "111111" then  --4AFC illegal
                                trap_illegal <= '1';
                                trapmake     <= '1';
                            else
                                if decodeOPC = '1' then
                                    ea_build <= '1';
                                end if;
                                if execOPC = '1' then
                                    dest_hbits     <= '1';         --for Flags
                                    source_lowbits <= '1';
                                --                                              IF opcode(3)='1' THEN                   --MC68020...
                                --                                                      source_areg <= '1';
                                --                                              END IF;
                                end if;
                                set_exec_MOVE <= '1';
                                if opcode(7 downto 6) = "11" then  --tas
                                    set_exec_tas <= '1';
                                    write_back   <= '1';
                                    datatype     <= "00";          --Byte
                                    if execOPC = '1' and endOPC = '1' then
                                        regwrena <= '1';
                                    end if;
                                end if;
                            end if;
--                                              WHEN "110"=>
                        when "111" =>   --4EXX
                            if opcode(7) = '1' then                --jsr, jmp
                                datatype <= "10";
                                ea_only  <= '1';
                                if nextpass = '1' and micro_state = idle then
                                    presub           <= '1';
                                    setstackaddr     <= '1';
                                    set_mem_addsub   <= '1';
                                    setstate         <= "11";
                                    next_micro_state <= nop;
                                end if;
                                if decodeOPC = '1' then
                                    ea_build <= '1';
                                end if;
                                if get_ea_now = '1' then           --jsr
                                    if opcode(6) = '0' then
                                        setstate <= "01";
                                    end if;
                                    ea_to_pc <= '1';
                                    if opcode(5 downto 1) = "11100" then
                                        writePC_add <= '1';
                                    else
                                        writePC <= '1';
                                    end if;
                                end if;
                            else        --
                                case opcode(6 downto 0) is
                                    when "1000000"|"1000001"|"1000010"|"1000011"|"1000100"|"1000101"|"1000110"|"1000111"|  --trap
                                                     "1001000"|"1001001"|"1001010"|"1001011"|"1001100"|"1001101"|"1001110"|"1001111" =>  --trap
                                        trap_trap <= '1';
                                        trapmake  <= '1';
                                    when "1010000"|"1010001"|"1010010"|"1010011"|"1010100"|"1010101"|"1010110"|"1010111" =>  --link
                                        datatype <= "10";
                                        if decodeOPC = '1' then
                                            next_micro_state <= link;
                                            set_exec_MOVE    <= '1';  --f�r displacement
                                            presub           <= '1';
                                            setstackaddr     <= '1';
                                            set_mem_addsub   <= '1';
                                            source_lowbits   <= '1';
                                            source_areg      <= '1';
                                        end if;
                                        if execOPC = '1' then
                                            setstackaddr <= '1';
                                            regwrena     <= '1';
                                        end if;

                                    when "1011000"|"1011001"|"1011010"|"1011011"|"1011100"|"1011101"|"1011110"|"1011111" =>  --unlink
                                        datatype <= "10";
                                        if decodeOPC = '1' then
                                            setstate     <= "10";
                                            set_mem_rega <= '1';
                                        elsif execOPC = '1' then
                                            regwrena <= '1';
                                            exec_exg <= '1';
                                        else
                                            setstackaddr <= '1';
                                            regwrena     <= '1';
                                            get_ea_now   <= '1';
                                            ea_only      <= '1';
                                        end if;

                                    when "1100000"|"1100001"|"1100010"|"1100011"|"1100100"|"1100101"|"1100110"|"1100111" =>  --move An,USP
                                        if SVmode = '1' then
                                            no_Flags       <= '1';
                                            to_USP         <= '1';
                                            setstackaddr   <= '1';
                                            source_lowbits <= '1';
                                            source_areg    <= '1';
                                            set_exec_MOVE  <= '1';
                                            datatype       <= "10";
                                            if execOPC = '1' then
                                                regwrena <= '1';
                                            end if;
                                        else
                                            trap_priv <= '1';
                                            trapmake  <= '1';
                                        end if;
                                    when "1101000"|"1101001"|"1101010"|"1101011"|"1101100"|"1101101"|"1101110"|"1101111" =>  --move USP,An
                                        if SVmode = '1' then
                                            no_Flags      <= '1';
                                            from_USP      <= '1';
                                            set_exec_MOVE <= '1';
                                            datatype      <= "10";
                                            if execOPC = '1' then
                                                regwrena <= '1';
                                            end if;
                                        else
                                            trap_priv <= '1';
                                            trapmake  <= '1';
                                        end if;

                                    when "1110000" =>  --reset
                                        if SVmode = '0' then
                                            trap_priv <= '1';
                                            trapmake  <= '1';
                                        end if;

                                    when "1110001" =>  --nop

                                    when "1110010" =>  --stop
                                        if SVmode = '0' then
                                            trap_priv <= '1';
                                            trapmake  <= '1';
                                        else
                                            if decodeOPC = '1' then
                                                setnextpass  <= '1';
                                                set_directSR <= '1';
                                                set_stop     <= '1';
                                            end if;
                                        end if;

                                    when "1110011" =>  --rte
                                        if SVmode = '1' then
                                            if decodeOPC = '1' then
                                                datatype         <= "01";
                                                setstate         <= "10";
                                                postadd          <= '1';
                                                setstackaddr     <= '1';
                                                set_mem_rega     <= '1';
                                                set_directSR     <= '1';
                                                next_micro_state <= rte;
                                            end if;
                                        else
                                            trap_priv <= '1';
                                            trapmake  <= '1';
                                        end if;

                                    when "1110101" =>  --rts
                                        if decodeOPC = '1' then
                                            datatype         <= "10";
                                            setstate         <= "10";
                                            postadd          <= '1';
                                            setstackaddr     <= '1';
                                            set_mem_rega     <= '1';
                                            set_directPC     <= '1';
                                            next_micro_state <= nop;
                                        end if;

                                    when "1110110" =>  --trapv
                                        if Flags(1) = '1' then
                                            trap_trapv <= '1';
                                            trapmake   <= '1';
                                        end if;

                                    when "1110111" =>  --rtr
                                        if decodeOPC = '1' then
                                            datatype         <= "01";
                                            setstate         <= "10";
                                            postadd          <= '1';
                                            setstackaddr     <= '1';
                                            set_mem_rega     <= '1';
                                            set_directCCR    <= '1';
                                            next_micro_state <= rte;
                                        end if;


                                    when others =>
                                        trap_illegal <= '1';
                                        trapmake     <= '1';
                                end case;
                            end if;
                        when others => null;
                    end case;
                end if;

-- 0101 ----------------------------------------------------------------------------
            when "0101" =>              --subq, addq

                if opcode(7 downto 6) = "11" then       --dbcc
                    if opcode(5 downto 3) = "001" then  --dbcc
                        datatype <= "01";               --Word
                        if decodeOPC = '1' then
                            next_micro_state <= nop;
                            OP2out_one       <= '1';
                            if condition = '0' then
                                Regwrena <= '1';
                                if c_in(2) = '1' then
                                    next_micro_state <= dbcc1;
                                end if;
                            end if;
                            data_is_source <= '1';
                        end if;
                    else                                --Scc
                        datatype   <= "00";             --Byte
                        write_back <= '1';
                        if decodeOPC = '1' then
                            ea_build <= '1';
                        end if;
                        if condition = '0' then
                            set_exec_Scc <= '1';
                        end if;
                        if execOPC = '1' then
                            if condition = '1' then
                                OP2out_one <= '1';
                                exec_EXG   <= '1';
                            else
                                OP1out_zero <= '1';
                            end if;
                            if endOPC = '1' then
                                Regwrena <= '1';
                            end if;
                        end if;
                    end if;
                else                                    --addq, subq
                    if decodeOPC = '1' then
                        ea_build <= '1';
                    end if;
                    if opcode(5 downto 3) = "001" then
                        no_Flags <= '1';
                    end if;
                    write_back    <= '1';
                    set_exec_ADDQ <= '1';
                    set_exec_ADD  <= '1';
                    if execOPC = '1' then
                        ea_data_OP1 <= '1';
                        if endOPC = '1' then
                            Regwrena <= '1';
                        end if;
                        if opcode(8) = '1' then
                            setaddsub <= '0';
                        end if;
                    end if;
                end if;

-- 0110 ----------------------------------------------------------------------------
            when "0110" =>              --bra,bsr,bcc
                datatype <= "10";

                if micro_state = idle then
                    if opcode(11 downto 8) = "0001" then  --bsr
                        if opcode(7 downto 0) = "00000000" then
                            next_micro_state <= bsr1;
                        else
                            next_micro_state <= bsr2;
                            setstate         <= "01";
                        end if;
                        presub         <= '1';
                        setstackaddr   <= '1';
                        set_mem_addsub <= '1';
                    else                                  --bra
                        if opcode(7 downto 0) = "00000000" then
                            next_micro_state <= bra1;
                        end if;
                        if condition = '1' then
                            TG68_PC_br8 <= '1';
                        end if;
                    end if;
                end if;

-- 0111 ----------------------------------------------------------------------------
            when "0111" =>                       --moveq
                if opcode(8) = '0' then
                    if trap_interrupt = '0' then
                        datatype       <= "10";  --Long
                        Regwrena       <= '1';
                        set_exec_MOVEQ <= '1';
                        set_exec_MOVE  <= '1';
                        dest_hbits     <= '1';
                    end if;
                else
                    trap_illegal <= '1';
                    trapmake     <= '1';
                end if;

-- 1000 ----------------------------------------------------------------------------
            when "1000" =>                             --or
                if opcode(7 downto 6) = "11" then      --divu, divs
                    if opcode(5 downto 4) = "00" then  --Dn, An
                        regdirectsource <= '1';
                    end if;
                    if (micro_state = idle and nextpass = '1') or (opcode(5 downto 4) = "00" and decodeOPC = '1') then
                        set_exec_DIVU    <= '1';
                        setstate         <= "01";
                        next_micro_state <= div1;
                    end if;
                    if decodeOPC = '1' then
                        ea_build <= '1';
                    end if;
                    if execOPC = '1' and z_error = '0' and set_V_Flag = '0' then
                        regwrena <= '1';
                    end if;
                    if (micro_state /= idle and nextpass = '1') or execOPC = '1' then
                        dest_hbits     <= '1';
                        source_lowbits <= '1';
                    else
                        datatype <= "01";
                    end if;


                elsif opcode(8) = '1' and opcode(5 downto 4) = "00" then  --sbcd, pack , unpack
                    if opcode(7 downto 6) = "00" then  --sbcd
                        use_XZFlag    <= '1';
                        set_exec_ADD  <= '1';
                        set_exec_SBCD <= '1';
                        if opcode(3) = '1' then
                            write_back <= '1';
                            if decodeOPC = '1' then
                                set_direct_data  <= '1';
                                setstate         <= "10";
                                set_mem_addsub   <= '1';
                                presub           <= '1';
                                next_micro_state <= op_AxAy;
                            end if;
                        end if;
                        if execOPC = '1' then
                            ea_data_OP1    <= '1';
                            dest_hbits     <= '1';
                            source_lowbits <= '1';
                            if endOPC = '1' then
                                Regwrena <= '1';
                            end if;
                        end if;
                    else                --pack, unpack
                        trap_illegal <= '1';
                        trapmake     <= '1';
                    end if;
                else                    --or
                    set_exec_OR <= '1';
                    if opcode(8) = '1' then
                        write_back <= '1';
                    end if;
                    if decodeOPC = '1' then
                        ea_build <= '1';
                    end if;
                    if execOPC = '1' then
                        if endOPC = '1' then
                            Regwrena <= '1';
                        end if;
                        if opcode(8) = '1' then
                            ea_data_OP1 <= '1';
                        else
                            dest_hbits     <= '1';
                            source_lowbits <= '1';
                            if opcode(3) = '1' then
                                source_areg <= '1';
                            end if;
                        end if;
                    end if;
                end if;

-- 1001, 1101 -----------------------------------------------------------------------
            when "1001"|"1101" =>       --sub, add
                set_exec_ADD <= '1';
                if decodeOPC = '1' then
                    ea_build <= '1';
                end if;
                if opcode(8 downto 6) = "011" then         --adda.w, suba.w
                    datatype <= "01";   --Word
                end if;
                if execOPC = '1' then
                    if endOPC = '1' then
                        Regwrena <= '1';
                    end if;
                    if opcode(14) = '0' then
                        setaddsub <= '0';
                    end if;
                end if;
                if opcode(8) = '1' and opcode(5 downto 4) = "00" and opcode(7 downto 6) /= "11" then  --addx, subx
                    use_XZFlag <= '1';
                    if opcode(3) = '1' then
                        write_back <= '1';
                        if decodeOPC = '1' then
                            set_direct_data  <= '1';
                            setstate         <= "10";
                            set_mem_addsub   <= '1';
                            presub           <= '1';
                            next_micro_state <= op_AxAy;
                        end if;
                    end if;
                    if execOPC = '1' then
                        ea_data_OP1    <= '1';
                        dest_hbits     <= '1';
                        source_lowbits <= '1';
                    end if;
                else                    --sub, add
                    if opcode(8) = '1' and opcode(7 downto 6) /= "11" then
                        write_back <= '1';
                    end if;
                    if execOPC = '1' then
                        if opcode(7 downto 6) = "11" then  --adda, suba
                            no_Flags       <= '1';
                            dest_areg      <= '1';
                            dest_hbits     <= '1';
                            source_lowbits <= '1';
                            if opcode(3) = '1' then
                                source_areg <= '1';
                            end if;
                        else
                            if opcode(8) = '1' then
                                ea_data_OP1 <= '1';
                            else
                                dest_hbits     <= '1';
                                source_lowbits <= '1';
                                if opcode(3) = '1' then
                                    source_areg <= '1';
                                end if;
                            end if;
                        end if;
                    end if;
                end if;

-- 1010 ----------------------------------------------------------------------------
            when "1010" =>              --Trap 1010
                trap_1010 <= '1';
                trapmake  <= '1';
-- 1011 ----------------------------------------------------------------------------
            when "1011" =>              --eor, cmp
                if decodeOPC = '1' then
                    ea_build <= '1';
                end if;
                if opcode(8 downto 6) = "011" then  --cmpa.w
                    datatype       <= "01";         --Word
                    set_exec_CPMAW <= '1';
                end if;
                if opcode(8) = '1' and opcode(5 downto 3) = "001" and opcode(7 downto 6) /= "11" then  --cmpm
                    set_exec_CMP <= '1';
                    if decodeOPC = '1' then
                        set_direct_data  <= '1';
                        setstate         <= "10";
                        set_mem_rega     <= '1';
                        postadd          <= '1';
                        next_micro_state <= cmpm;
                    end if;
                    if execOPC = '1' then
                        ea_data_OP1 <= '1';
                        setaddsub   <= '0';
                    end if;
                else                    --sub, add
                    if opcode(8) = '1' and opcode(7 downto 6) /= "11" then  --eor
                        set_exec_EOR <= '1';
                        write_back   <= '1';
                    else                --cmp
                        set_exec_CMP <= '1';
                    end if;

                    if execOPC = '1' then
                        if opcode(8) = '1' and opcode(7 downto 6) /= "11" then  --eor
                            ea_data_OP1 <= '1';
                            if endOPC = '1' then
                                Regwrena <= '1';
                            end if;
                        else            --cmp
                            source_lowbits <= '1';
                            if opcode(3) = '1' then
                                source_areg <= '1';
                            end if;
                            if opcode(7 downto 6) = "11" then  --cmpa
                                dest_areg <= '1';
                            end if;
                            dest_hbits <= '1';
                            setaddsub  <= '0';
                        end if;
                    end if;
                end if;

-- 1100 ----------------------------------------------------------------------------
            when "1100" =>                             --and, exg
                if opcode(7 downto 6) = "11" then      --mulu, muls
                    if opcode(5 downto 4) = "00" then  --Dn, An
                        regdirectsource <= '1';
                    end if;
                    if (micro_state = idle and nextpass = '1') or (opcode(5 downto 4) = "00" and decodeOPC = '1') then
                        set_exec_MULU    <= '1';
                        setstate         <= "01";
                        next_micro_state <= mul1;
                    end if;
                    if decodeOPC = '1' then
                        ea_build <= '1';
                    end if;
                    if execOPC = '1' then
                        regwrena <= '1';
                    end if;
                    if (micro_state /= idle and nextpass = '1') or execOPC = '1' then
                        dest_hbits     <= '1';
                        source_lowbits <= '1';
                    else
                        datatype <= "01";
                    end if;

                elsif opcode(8) = '1' and opcode(5 downto 4) = "00" then  --exg, abcd
                    if opcode(7 downto 6) = "00" then  --abcd
                        use_XZFlag    <= '1';
--                                              datatype <= "00";               --ist schon default
                        set_exec_ADD  <= '1';
                        set_exec_ABCD <= '1';
                        if opcode(3) = '1' then
                            write_back <= '1';
                            if decodeOPC = '1' then
                                set_direct_data  <= '1';
                                setstate         <= "10";
                                set_mem_addsub   <= '1';
                                presub           <= '1';
                                next_micro_state <= op_AxAy;
                            end if;
                        end if;
                        if execOPC = '1' then
                            ea_data_OP1    <= '1';
                            dest_hbits     <= '1';
                            source_lowbits <= '1';
                            if endOPC = '1' then
                                Regwrena <= '1';
                            end if;
                        end if;
                    else                --exg
                        datatype <= "10";
                        regwrena <= '1';
                        if opcode(6) = '1' and opcode(3) = '1' then
                            dest_areg   <= '1';
                            source_areg <= '1';
                        end if;
                        if decodeOPC = '1' then
                            set_mem_rega <= '1';
                            exec_exg     <= '1';
                        else
                            save_memaddr <= '1';
                            dest_hbits   <= '1';
                        end if;
                    end if;
                else                    --and
                    set_exec_AND <= '1';
                    if opcode(8) = '1' then
                        write_back <= '1';
                    end if;
                    if decodeOPC = '1' then
                        ea_build <= '1';
                    end if;

                    if execOPC = '1' then
                        if endOPC = '1' then
                            Regwrena <= '1';
                        end if;
                        if opcode(8) = '1' then
                            ea_data_OP1 <= '1';
                        else
                            dest_hbits     <= '1';
                            source_lowbits <= '1';
                            if opcode(3) = '1' then
                                source_areg <= '1';
                            end if;
                        end if;
                    end if;
                end if;

-- 1110 ----------------------------------------------------------------------------
            when "1110" =>              --rotation
                set_exec_ROT <= '1';
                if opcode(7 downto 6) = "11" then
                    datatype    <= "01";
                    rot_bits    <= opcode(10 downto 9);
                    ea_data_OP1 <= '1';
                    write_back  <= '1';
                else
                    rot_bits       <= opcode(4 downto 3);
                    data_is_source <= '1';
                end if;

                if decodeOPC = '1' then
                    if opcode(7 downto 6) = "11" then
                        ea_build <= '1';
                    else
                        if opcode(5) = '1' then
                            if OP2out(5 downto 0) /= "000000" then
                                set_rot_cnt <= OP2out(5 downto 0);
                            else
                                set_rot_nop <= '1';
                            end if;
                        else
                            set_rot_cnt(2 downto 0) <= opcode(11 downto 9);
                            if opcode(11 downto 9) = "000" then
                                set_rot_cnt(3) <= '1';
                            else
                                set_rot_cnt(3) <= '0';
                            end if;
                        end if;
                    end if;
                end if;
                if opcode(7 downto 6) /= "11" then
                    if execOPC = '1' and rot_nop = '0' then
                        Regwrena    <= '1';
                        set_rot_cnt <= rot_cnt-1;
                    end if;
                end if;

--      ----------------------------------------------------------------------------
            when others =>
                trap_1111 <= '1';
                trapmake  <= '1';

        end case;

--      END PROCESS;

-----------------------------------------------------------------------------
-- execute microcode
-----------------------------------------------------------------------------
--PROCESS (micro_state)
--      BEGIN
        if Z_error = '1' then           -- divu by zero
            trapmake <= '1';            --wichtig f�r USP
            if trapd = '0' then
                writePC <= '1';
            end if;
        end if;

        if trapmake = '1' and trapd = '0' then
            next_micro_state <= trap1;
            presub           <= '1';
            setstackaddr     <= '1';
            set_mem_addsub   <= '1';
            setstate         <= "11";
            datatype         <= "10";
        end if;

        if interrupt = '1' then
            next_micro_state <= int1;
            setstate         <= "10";
--                      datatype <= "01";               --wirkt sich auf Flags aus
        end if;

        if reset = '0' then
            micro_state <= init1;
        elsif rising_edge(clk) then
            if clkena = '1' then
                trapd <= trapmake;
                if fetchOPC = '1' then
                    micro_state <= idle;
                else
                    micro_state <= next_micro_state;
                end if;
            end if;
        end if;
        case micro_state is
            when ld_nn =>               -- (nnnn).w/l=>
                get_ea_now  <= '1';
                setnextpass <= '1';
                setaddrlong <= '1';

            when st_nn =>               -- =>(nnnn).w/l
                setstate         <= "11";
                setaddrlong      <= '1';
                next_micro_state <= nop;

            when ld_dAn1 =>             -- d(An)=>, --d(PC)=>
                setstate         <= "01";
                next_micro_state <= ld_dAn2;
            when ld_dAn2 =>             -- d(An)=>, --d(PC)=>
                get_ea_now  <= '1';
                setdisp     <= '1';     --word
                setnextpass <= '1';

            when ld_AnXn1 =>              -- d(An,Xn)=>, --d(PC,Xn)=>
                setstate         <= "01";
                next_micro_state <= ld_AnXn2;
            when ld_AnXn2 =>              -- d(An,Xn)=>, --d(PC,Xn)=>
                setdisp          <= '1';  --byte
                setdispbyte      <= '1';
                setstate         <= "01";
                setbriefext      <= '1';
                next_micro_state <= ld_AnXn3;
            when ld_AnXn3 =>
                get_ea_now   <= '1';
                setdisp      <= '1';      --brief
                setdispbrief <= '1';
                setnextpass  <= '1';

            when st_dAn1 =>               -- =>d(An)
                setstate         <= "01";
                next_micro_state <= st_dAn2;
            when st_dAn2 =>               -- =>d(An)
                setstate         <= "11";
                setdisp          <= '1';  --word
                next_micro_state <= nop;

            when st_AnXn1 =>              -- =>d(An,Xn)
                setstate         <= "01";
                next_micro_state <= st_AnXn2;
            when st_AnXn2 =>              -- =>d(An,Xn)
                setdisp          <= '1';  --byte
                setdispbyte      <= '1';
                setstate         <= "01";
                setbriefext      <= '1';
                next_micro_state <= st_AnXn3;
            when st_AnXn3 =>
                setstate         <= "11";
                setdisp          <= '1';  --brief
                setdispbrief     <= '1';
                next_micro_state <= nop;

            when bra1 =>                      --bra
                if condition = '1' then
                    TG68_PC_br8      <= '1';  --pc+0000
                    setstate         <= "01";
                    next_micro_state <= bra2;
                end if;
            when bra2 =>                      --bra
                TG68_PC_brw <= '1';

            when bsr1 =>                  --bsr
                set_TG68_PC_dec  <= '1';  --in 2 Takten -2
                setstate         <= "01";
                next_micro_state <= bsr2;
            when bsr2 =>                  --bsr
                if TG68_PC_dec(0) = '1' then
                    TG68_PC_brw <= '1';
                else
                    TG68_PC_br8 <= '1';
                end if;
                writePC          <= '1';
                setstate         <= "11";
                next_micro_state <= nop;

            when dbcc1 =>               --dbcc
                TG68_PC_nop      <= '1';
                setstate         <= "01";
                next_micro_state <= dbcc2;
            when dbcc2 =>               --dbcc
                TG68_PC_brw <= '1';

            when movem =>               --movem
                set_movem_busy <= '1';
                setstate       <= "10";

            when andi =>                --andi
                if opcode(5 downto 4) /= "00" then
                    ea_build    <= '1';
                    setnextpass <= '1';
                end if;

            when op_AxAy =>             -- op -(Ax),-(Ay)
                presub         <= '1';
                dest_hbits     <= '1';
                dest_areg      <= '1';
                set_mem_addsub <= '1';
                setstate       <= "10";

            when cmpm =>                -- cmpm (Ay)+,(Ax)+
                postadd      <= '1';
                dest_hbits   <= '1';
                dest_areg    <= '1';
                set_mem_rega <= '1';
                setstate     <= "10";

            when link =>                -- link
                setstate     <= "11";
                save_memaddr <= '1';
                regwrena     <= '1';

            when int1 =>                -- interrupt
                presub           <= '1';
                setstackaddr     <= '1';
                set_mem_addsub   <= '1';
                setstate         <= "11";
                datatype         <= "10";
                next_micro_state <= int2;
            when int2 =>                -- interrupt
                presub           <= '1';
                setstackaddr     <= '1';
                set_mem_addsub   <= '1';
                setstate         <= "11";
                datatype         <= "01";
                writeSR          <= '1';
                next_micro_state <= int3;
            when int3 =>                -- interrupt
                set_vectoraddr   <= '1';
                datatype         <= "10";
                set_directPC     <= '1';
                setstate         <= "10";
                next_micro_state <= int4;
            when int4 =>                -- interrupt
                datatype <= "10";

            when rte =>                 -- RTE
                datatype         <= "10";
                setstate         <= "10";
                postadd          <= '1';
                setstackaddr     <= '1';
                set_mem_rega     <= '1';
                set_directPC     <= '1';
                next_micro_state <= nop;

            when trap1 =>               -- TRAP
                presub           <= '1';
                setstackaddr     <= '1';
                set_mem_addsub   <= '1';
                setstate         <= "11";
                datatype         <= "01";
                writeSR          <= '1';
                next_micro_state <= trap2;
            when trap2 =>               -- TRAP
                set_vectoraddr   <= '1';
                datatype         <= "10";
                set_directPC     <= '1';
--                                      longreaddirect <= '1';
                setstate         <= "10";
                next_micro_state <= trap3;
            when trap3 =>               -- TRAP
                datatype <= "10";

            when movep1 =>              -- MOVEP d(An)
                setstate <= "01";
                if opcode(6) = '1' then
                    set_movepl <= '1';
                end if;
                next_micro_state <= movep2;
            when movep2 =>
                setdisp <= '1';
                if opcode(7) = '0' then
                    setstate <= "10";
                else
                    setstate      <= "11";
                    wait_mem_byte <= '1';
                end if;
                next_micro_state <= movep3;
            when movep3 =>
                if opcode(6) = '1' then
                    set_movepw       <= '1';
                    next_micro_state <= movep4;
                end if;
                if opcode(7) = '0' then
                    setstate <= "10";
                else
                    setstate <= "11";
                end if;
            when movep4 =>
                if opcode(7) = '0' then
                    setstate <= "10";
                else
                    wait_mem_byte <= '1';
                    setstate      <= "11";
                end if;
                next_micro_state <= movep5;
            when movep5 =>
                if opcode(7) = '0' then
                    setstate <= "10";
                else
                    setstate <= "11";
                end if;

            when init1 =>                 -- init SP
                longreaddirect   <= '1';
                next_micro_state <= init2;
            when init2 =>                 -- init PC
                get_ea_now       <= '1';  --\
                ea_only          <= '1';  ---  OP1in <= memaddr_in
                setaddrlong      <= '1';  --   memaddr_in <= data_read
                regwrena         <= '1';
                setstackaddr     <= '1';  --   dest_addr <= SP
                set_directPC     <= '1';
                longreaddirect   <= '1';
                next_micro_state <= nop;

            when mul1 =>                -- mulu
                set_exec_MULU    <= '1';
                setstate         <= "01";
                next_micro_state <= mul2;
            when mul2 =>                -- mulu
                set_exec_MULU    <= '1';
                setstate         <= "01";
                next_micro_state <= mul3;
            when mul3 =>                -- mulu
                set_exec_MULU    <= '1';
                setstate         <= "01";
                next_micro_state <= mul4;
            when mul4 =>                -- mulu
                set_exec_MULU    <= '1';
                setstate         <= "01";
                next_micro_state <= mul5;
            when mul5 =>                -- mulu
                set_exec_MULU    <= '1';
                setstate         <= "01";
                next_micro_state <= mul6;
            when mul6 =>                -- mulu
                set_exec_MULU    <= '1';
                setstate         <= "01";
                next_micro_state <= mul7;
            when mul7 =>                -- mulu
                set_exec_MULU    <= '1';
                setstate         <= "01";
                next_micro_state <= mul8;
            when mul8 =>                -- mulu
                set_exec_MULU    <= '1';
                setstate         <= "01";
                next_micro_state <= mul9;
            when mul9 =>                -- mulu
                set_exec_MULU    <= '1';
                setstate         <= "01";
                next_micro_state <= mul10;
            when mul10 =>               -- mulu
                set_exec_MULU    <= '1';
                setstate         <= "01";
                next_micro_state <= mul11;
            when mul11 =>               -- mulu
                set_exec_MULU    <= '1';
                setstate         <= "01";
                next_micro_state <= mul12;
            when mul12 =>               -- mulu
                set_exec_MULU    <= '1';
                setstate         <= "01";
                next_micro_state <= mul13;
            when mul13 =>               -- mulu
                set_exec_MULU    <= '1';
                setstate         <= "01";
                next_micro_state <= mul14;
            when mul14 =>               -- mulu
                set_exec_MULU    <= '1';
                setstate         <= "01";
                next_micro_state <= mul15;
            when mul15 =>               -- mulu
                set_exec_MULU <= '1';

            when div1 =>                               -- divu
                if OP2out(15 downto 0) = x"0000" then  --div zero
                    set_Z_error <= '1';
                else
                    set_exec_DIVU    <= '1';
                    next_micro_state <= div2;
                end if;
                setstate <= "01";
            when div2 =>                               -- divu
                set_exec_DIVU    <= '1';
                setstate         <= "01";
                next_micro_state <= div3;
            when div3 =>                               -- divu
                set_exec_DIVU    <= '1';
                setstate         <= "01";
                next_micro_state <= div4;
            when div4 =>                               -- divu
                set_exec_DIVU    <= '1';
                setstate         <= "01";
                next_micro_state <= div5;
            when div5 =>                               -- divu
                set_exec_DIVU    <= '1';
                setstate         <= "01";
                next_micro_state <= div6;
            when div6 =>                               -- divu
                set_exec_DIVU    <= '1';
                setstate         <= "01";
                next_micro_state <= div7;
            when div7 =>                               -- divu
                set_exec_DIVU    <= '1';
                setstate         <= "01";
                next_micro_state <= div8;
            when div8 =>                               -- divu
                set_exec_DIVU    <= '1';
                setstate         <= "01";
                next_micro_state <= div9;
            when div9 =>                               -- divu
                set_exec_DIVU    <= '1';
                setstate         <= "01";
                next_micro_state <= div10;
            when div10 =>                              -- divu
                set_exec_DIVU    <= '1';
                setstate         <= "01";
                next_micro_state <= div11;
            when div11 =>                              -- divu
                set_exec_DIVU    <= '1';
                setstate         <= "01";
                next_micro_state <= div12;
            when div12 =>                              -- divu
                set_exec_DIVU    <= '1';
                setstate         <= "01";
                next_micro_state <= div13;
            when div13 =>                              -- divu
                set_exec_DIVU    <= '1';
                setstate         <= "01";
                next_micro_state <= div14;
            when div14 =>                              -- divu
                set_exec_DIVU    <= '1';
                setstate         <= "01";
                next_micro_state <= div15;
            when div15 =>                              -- divu
                set_exec_DIVU <= '1';

            when others => null;
        end case;
    end process;

-----------------------------------------------------------------------------
-- Conditions
-----------------------------------------------------------------------------
    process (opcode, Flags)
    begin
        case opcode(11 downto 8) is
            when X"0"   => condition <= '1';
            when X"1"   => condition <= '0';
            when X"2"   => condition <= not Flags(0) and not Flags(2);
            when X"3"   => condition <= Flags(0) or Flags(2);
            when X"4"   => condition <= not Flags(0);
            when X"5"   => condition <= Flags(0);
            when X"6"   => condition <= not Flags(2);
            when X"7"   => condition <= Flags(2);
            when X"8"   => condition <= not Flags(1);
            when X"9"   => condition <= Flags(1);
            when X"a"   => condition <= not Flags(3);
            when X"b"   => condition <= Flags(3);
            when X"c"   => condition <= (Flags(3) and Flags(1)) or (not Flags(3) and not Flags(1));
            when X"d"   => condition <= (Flags(3) and not Flags(1)) or (not Flags(3) and Flags(1));
            when X"e"   => condition <= (Flags(3) and Flags(1) and not Flags(2)) or (not Flags(3) and not Flags(1) and not Flags(2));
            when X"f"   => condition <= (Flags(3) and not Flags(1)) or (not Flags(3) and Flags(1)) or Flags(2);
            when others => null;
        end case;
    end process;

-----------------------------------------------------------------------------
-- Bits
-----------------------------------------------------------------------------
    process (opcode, OP1out, OP2out, one_bit_in, one_bit_out, bit_Number, bit_number_reg)
    begin
        case opcode(7 downto 6) is
            when "00" =>                --btst
                one_bit_out <= one_bit_in;
            when "01" =>                --bchg
                one_bit_out <= not one_bit_in;
            when "10" =>                --bclr
                one_bit_out <= '0';
            when "11" =>                --bset
                one_bit_out <= '1';
            when others => null;
        end case;

        if opcode(8) = '0' then
            if opcode(5 downto 4) = "00" then
                bit_number <= bit_number_reg(4 downto 0);
            else
                bit_number <= "00"&bit_number_reg(2 downto 0);
            end if;
        else
            if opcode(5 downto 4) = "00" then
                bit_number <= OP2out(4 downto 0);
            else
                bit_number <= "00"&OP2out(2 downto 0);
            end if;
        end if;

        bits_out <= OP1out;
        case bit_Number is
            when "00000" => one_bit_in <= OP1out(0);
                            bits_out(0) <= one_bit_out;
            when "00001" => one_bit_in <= OP1out(1);
                            bits_out(1) <= one_bit_out;
            when "00010" => one_bit_in <= OP1out(2);
                            bits_out(2) <= one_bit_out;
            when "00011" => one_bit_in <= OP1out(3);
                            bits_out(3) <= one_bit_out;
            when "00100" => one_bit_in <= OP1out(4);
                            bits_out(4) <= one_bit_out;
            when "00101" => one_bit_in <= OP1out(5);
                            bits_out(5) <= one_bit_out;
            when "00110" => one_bit_in <= OP1out(6);
                            bits_out(6) <= one_bit_out;
            when "00111" => one_bit_in <= OP1out(7);
                            bits_out(7) <= one_bit_out;
            when "01000" => one_bit_in <= OP1out(8);
                            bits_out(8) <= one_bit_out;
            when "01001" => one_bit_in <= OP1out(9);
                            bits_out(9) <= one_bit_out;
            when "01010" => one_bit_in <= OP1out(10);
                            bits_out(10) <= one_bit_out;
            when "01011" => one_bit_in <= OP1out(11);
                            bits_out(11) <= one_bit_out;
            when "01100" => one_bit_in <= OP1out(12);
                            bits_out(12) <= one_bit_out;
            when "01101" => one_bit_in <= OP1out(13);
                            bits_out(13) <= one_bit_out;
            when "01110" => one_bit_in <= OP1out(14);
                            bits_out(14) <= one_bit_out;
            when "01111" => one_bit_in <= OP1out(15);
                            bits_out(15) <= one_bit_out;
            when "10000" => one_bit_in <= OP1out(16);
                            bits_out(16) <= one_bit_out;
            when "10001" => one_bit_in <= OP1out(17);
                            bits_out(17) <= one_bit_out;
            when "10010" => one_bit_in <= OP1out(18);
                            bits_out(18) <= one_bit_out;
            when "10011" => one_bit_in <= OP1out(19);
                            bits_out(19) <= one_bit_out;
            when "10100" => one_bit_in <= OP1out(20);
                            bits_out(20) <= one_bit_out;
            when "10101" => one_bit_in <= OP1out(21);
                            bits_out(21) <= one_bit_out;
            when "10110" => one_bit_in <= OP1out(22);
                            bits_out(22) <= one_bit_out;
            when "10111" => one_bit_in <= OP1out(23);
                            bits_out(23) <= one_bit_out;
            when "11000" => one_bit_in <= OP1out(24);
                            bits_out(24) <= one_bit_out;
            when "11001" => one_bit_in <= OP1out(25);
                            bits_out(25) <= one_bit_out;
            when "11010" => one_bit_in <= OP1out(26);
                            bits_out(26) <= one_bit_out;
            when "11011" => one_bit_in <= OP1out(27);
                            bits_out(27) <= one_bit_out;
            when "11100" => one_bit_in <= OP1out(28);
                            bits_out(28) <= one_bit_out;
            when "11101" => one_bit_in <= OP1out(29);
                            bits_out(29) <= one_bit_out;
            when "11110" => one_bit_in <= OP1out(30);
                            bits_out(30) <= one_bit_out;
            when "11111" => one_bit_in <= OP1out(31);
                            bits_out(31) <= one_bit_out;
            when others => null;
        end case;
    end process;

-----------------------------------------------------------------------------
-- Rotation
-----------------------------------------------------------------------------
    process (opcode, OP1out, Flags, rot_bits, rot_msb, rot_lsb, rot_rot, rot_nop)
    begin
        case opcode(7 downto 6) is
            when "00" =>                --Byte
                rot_rot <= OP1out(7);
            when "01"|"11" =>           --Word
                rot_rot <= OP1out(15);
            when "10" =>                --Long
                rot_rot <= OP1out(31);
            when others => null;
        end case;

        case rot_bits is
            when "00" =>                --ASL, ASR
                rot_lsb <= '0';
                rot_msb <= rot_rot;
            when "01" =>                --LSL, LSR
                rot_lsb <= '0';
                rot_msb <= '0';
            when "10" =>                --ROXL, ROXR
                rot_lsb <= Flags(4);
                rot_msb <= Flags(4);
            when "11" =>                --ROL, ROR
                rot_lsb <= rot_rot;
                rot_msb <= OP1out(0);
            when others => null;
        end case;

        if rot_nop = '1' then
            rot_out <= OP1out;
            rot_XC  <= Flags(0);
        else
            if opcode(8) = '1' then     --left
                rot_out <= OP1out(30 downto 0)&rot_lsb;
                rot_XC  <= rot_rot;
            else                        --right
                rot_XC  <= OP1out(0);
                rot_out <= rot_msb&OP1out(31 downto 1);
                case opcode(7 downto 6) is
                    when "00" =>        --Byte
                        rot_out(7) <= rot_msb;
                    when "01"|"11" =>   --Word
                        rot_out(15) <= rot_msb;
                    when others =>
                end case;
            end if;
        end if;
    end process;

-----------------------------------------------------------------------------
-- MULU/MULS
-----------------------------------------------------------------------------
    process (clk, opcode, OP2out, muls_msb, mulu_reg, OP1sign, sign2)
    begin
        if rising_edge(clk) then
            if clkena = '1' then
                if decodeOPC = '1' then
                    if opcode(8) = '1' and reg_QB(15) = '1' then  --MULS Neg faktor
                        OP1sign  <= '1';
                        mulu_reg <= "0000000000000000"&(0-reg_QB(15 downto 0));
                    else
                        OP1sign  <= '0';
                        mulu_reg <= "0000000000000000"&reg_QB(15 downto 0);
                    end if;
                elsif exec_MULU = '1' then
                    mulu_reg <= dummy_mulu;
                end if;
            end if;
        end if;

        if (opcode(8) = '1' and OP2out(15) = '1') or OP1sign = '1' then
            muls_msb <= mulu_reg(31);
        else
            muls_msb <= '0';
        end if;

        if opcode(8) = '1' and OP2out(15) = '1' then
            sign2 <= '1';
        else
            sign2 <= '0';
        end if;

        if mulu_reg(0) = '1' then
            if OP1sign = '1' then
                dummy_mulu <= (muls_msb&mulu_reg(31 downto 16))-(sign2&OP2out(15 downto 0))& mulu_reg(15 downto 1);
            else
                dummy_mulu <= (muls_msb&mulu_reg(31 downto 16))+(sign2&OP2out(15 downto 0))& mulu_reg(15 downto 1);
            end if;
        else
            dummy_mulu <= muls_msb&mulu_reg(31 downto 1);
        end if;
    end process;

-----------------------------------------------------------------------------
-- DIVU
-----------------------------------------------------------------------------
    process (clk, execOPC, opcode, OP1out, OP2out, div_reg, dummy_div_sub, div_quot, div_sign, dummy_div_over, dummy_div)
    begin
        set_V_Flag <= '0';

        if rising_edge(clk) then
            if clkena = '1' then
                if decodeOPC = '1' then
                    if opcode(8) = '1' and reg_QB(31) = '1' then  -- Neg divisor
                        div_sign <= '1';
                        div_reg  <= 0-reg_QB;
                    else
                        div_sign <= '0';
                        div_reg  <= reg_QB;
                    end if;
                elsif exec_DIVU = '1' then
                    div_reg <= div_quot;
                end if;
            end if;
        end if;

        dummy_div_over <= ('0'&OP1out(31 downto 16))-('0'&OP2out(15 downto 0));

        if opcode(8) = '1' and OP2out(15) = '1' then
            dummy_div_sub <= (div_reg(31 downto 15))+('1'&OP2out(15 downto 0));
        else
            dummy_div_sub <= (div_reg(31 downto 15))-('0'&OP2out(15 downto 0));
        end if;

        if (dummy_div_sub(16)) = '1' then
            div_quot(31 downto 16) <= div_reg(30 downto 15);
        else
            div_quot(31 downto 16) <= dummy_div_sub(15 downto 0);
        end if;

        div_quot(15 downto 0) <= div_reg(14 downto 0)&not dummy_div_sub(16);

        if execOPC = '1' and opcode(8) = '1' and (OP2out(15) xor div_sign) = '1' then
            dummy_div(15 downto 0) <= 0-div_quot(15 downto 0);
        else
            dummy_div(15 downto 0) <= div_quot(15 downto 0);
        end if;

        if div_sign = '1' then
            dummy_div(31 downto 16) <= 0-div_quot(31 downto 16);
        else
            dummy_div(31 downto 16) <= div_quot(31 downto 16);
        end if;

        if (opcode(8) = '1' and (OP2out(15) xor div_sign xor dummy_div(15)) = '1' and dummy_div(15 downto 0) /= X"0000")  --Overflow DIVS
                          or (opcode(8) = '0' and dummy_div_over(16) = '0') then  --Overflow DIVU
            set_V_Flag <= '1';
        end if;
    end process;

-----------------------------------------------------------------------------
-- Movem
-----------------------------------------------------------------------------
    process (reset, clk, movem_mask, movem_muxa, movem_muxb, movem_muxc)
    begin
        if movem_mask(7 downto 0) = "00000000" then
            movem_muxa       <= movem_mask(15 downto 8);
            movem_regaddr(3) <= '1';
        else
            movem_muxa       <= movem_mask(7 downto 0);
            movem_regaddr(3) <= '0';
        end if;
        if movem_muxa(3 downto 0) = "0000" then
            movem_muxb       <= movem_muxa(7 downto 4);
            movem_regaddr(2) <= '1';
        else
            movem_muxb       <= movem_muxa(3 downto 0);
            movem_regaddr(2) <= '0';
        end if;
        if movem_muxb(1 downto 0) = "00" then
            movem_muxc       <= movem_muxb(3 downto 2);
            movem_regaddr(1) <= '1';
        else
            movem_muxc       <= movem_muxb(1 downto 0);
            movem_regaddr(1) <= '0';
        end if;
        if movem_muxc(0) = '0' then
            movem_regaddr(0) <= '1';
        else
            movem_regaddr(0) <= '0';
        end if;

        movem_bits <= ("0000"&movem_mask(0))+("0000"&movem_mask(1))+("0000"&movem_mask(2))+("0000"&movem_mask(3))+
                      ("0000"&movem_mask(4))+("0000"&movem_mask(5))+("0000"&movem_mask(6))+("0000"&movem_mask(7))+
                      ("0000"&movem_mask(8))+("0000"&movem_mask(9))+("0000"&movem_mask(10))+("0000"&movem_mask(11))+
                      ("0000"&movem_mask(12))+("0000"&movem_mask(13))+("0000"&movem_mask(14))+("0000"&movem_mask(15));

        if reset = '0' then
            movem_busy <= '0';
            movem_addr <= '0';
            maskzero   <= '0';
        elsif rising_edge(clk) then
            if clkena_in = '1' and get_movem_mask = '1' then
                movem_mask <= data_read(15 downto 0);
            end if;
            if clkena_in = '1' and test_maskzero = '1' then
                if movem_mask = X"0000" then
                    maskzero <= '1';
                end if;
            end if;
            if clkena_in = '1' and endOPC = '1' then
                maskzero <= '0';
            end if;
            if clkena = '1' then
                if set_movem_busy = '1' then
                    if movem_bits(4 downto 1) /= "0000" or opcode(10) = '0' then
                        movem_busy <= '1';
                    end if;
                    movem_addr <= '1';
                end if;
                if movem_addr = '1' then
                    case movem_regaddr is
                        when "0000" => movem_mask(0)  <= '0';
                        when "0001" => movem_mask(1)  <= '0';
                        when "0010" => movem_mask(2)  <= '0';
                        when "0011" => movem_mask(3)  <= '0';
                        when "0100" => movem_mask(4)  <= '0';
                        when "0101" => movem_mask(5)  <= '0';
                        when "0110" => movem_mask(6)  <= '0';
                        when "0111" => movem_mask(7)  <= '0';
                        when "1000" => movem_mask(8)  <= '0';
                        when "1001" => movem_mask(9)  <= '0';
                        when "1010" => movem_mask(10) <= '0';
                        when "1011" => movem_mask(11) <= '0';
                        when "1100" => movem_mask(12) <= '0';
                        when "1101" => movem_mask(13) <= '0';
                        when "1110" => movem_mask(14) <= '0';
                        when "1111" => movem_mask(15) <= '0';
                        when others => null;
                    end case;
                    if opcode(10) = '1' then
                        if movem_bits = "00010" or movem_bits = "00001" or movem_bits = "00000" then
                            movem_busy <= '0';
                        end if;
                    end if;
                    if movem_bits = "00001" or movem_bits = "00000" then
                        movem_busy <= '0';
                        movem_addr <= '0';
                    end if;
                end if;
            end if;
        end if;
    end process;
end;
