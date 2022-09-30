--
-- Z80 compatible microprocessor core, synchronous top level
-- Different timing than the original z80
-- Inputs needs to be synchronous and outputs may glitch
--
-- Version : 0242
--
-- Copyright (c) 2001-2002 Daniel Wallner (jesus@opencores.org)
--
-- All rights reserved
--
-- Redistribution and use in source and synthezised forms, with or without
-- modification, are permitted provided that the following conditions are met:
--
-- Redistributions of source code must retain the above copyright notice,
-- this list of conditions and the following disclaimer.
--
-- Redistributions in synthesized form must reproduce the above copyright
-- notice, this list of conditions and the following disclaimer in the
-- documentation and/or other materials provided with the distribution.
--
-- Neither the name of the author nor the names of other contributors may
-- be used to endorse or promote products derived from this software without
-- specific prior written permission.
--
-- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
-- AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
-- THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
-- PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE
-- LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
-- CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
-- SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
-- INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
-- CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
-- ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
-- POSSIBILITY OF SUCH DAMAGE.
--
-- Please report bugs to the author, but before you do so, please
-- make sure that this is not a derivative work and that
-- you have the latest version of this file.
--
-- The latest version of this file can be found at:
--	http://www.opencores.org/cvsweb.shtml/t80/
--
-- Limitations :
--
-- File history :
--
--	0208 : First complete release
--
--	0210 : Fixed read with wait
--
--	0211 : Fixed interrupt cycle
--
--	0235 : Updated for T80 interface change
--
--	0236 : Added T2Write generic
--
--	0237 : Fixed T2Write with wait state
--
--	0238 : Updated for T80 interface change
--
--	0240 : Updated for T80 interface change
--
--	0242 : Updated for T80 interface change
--

library IEEE;
use IEEE.std_logic_1164.all;
use IEEE.numeric_std.all;
use IEEE.STD_LOGIC_UNSIGNED.all;

entity T80s is
	generic(
		Mode    : integer := 0; -- 0 => Z80, 1 => Fast Z80, 2 => 8080, 3 => GB
		T2Write : integer := 1; -- 0 => WR_n active in T3, /=0 => WR_n active in T2
		IOWait  : integer := 1  -- 0 => Single cycle I/O, 1 => Std I/O cycle
	);
	port(
		RESET_n : in std_logic;
		CLK     : in std_logic;
		CEN     : in std_logic := '1';
		WAIT_n  : in std_logic := '1';
		INT_n	  : in std_logic := '1';
		NMI_n	  : in std_logic := '1';
		BUSRQ_n : in std_logic := '1';
		M1_n    : out std_logic;
		MREQ_n  : out std_logic;
		IORQ_n  : out std_logic;
		RD_n    : out std_logic;
		WR_n    : out std_logic;
		RFSH_n  : out std_logic;
		HALT_n  : out std_logic;
		BUSAK_n : out std_logic;
		OUT0    : in  std_logic := '0';  -- 0 => OUT(C),0, 1 => OUT(C),255
		A       : out std_logic_vector(15 downto 0);
		DI      : in std_logic_vector(7 downto 0);
		DO      : out std_logic_vector(7 downto 0);
		REG     : out std_logic_vector(211 downto 0)
	);
end T80s;

architecture rtl of T80s is
    component T80
        generic(
            Mode   : integer := 0;  -- 0 => Z80, 1 => Fast Z80, 2 => 8080, 3 => GB
            IOWait : integer := 0;  -- 0 => Single cycle I/O, 1 => Std I/O cycle
            Flag_C : integer := 0;
            Flag_N : integer := 1;
            Flag_P : integer := 2;
            Flag_X : integer := 3;
            Flag_H : integer := 4;
            Flag_Y : integer := 5;
            Flag_Z : integer := 6;
            Flag_S : integer := 7
        );
        port(
            RESET_n    : in  std_logic;
            CLK_n      : in  std_logic;
            CEN        : in  std_logic;
            WAIT_n     : in  std_logic;
            INT_n      : in  std_logic;
            NMI_n      : in  std_logic;
            BUSRQ_n    : in  std_logic;
            M1_n       : out std_logic;
            IORQ       : out std_logic;
            NoRead     : out std_logic;
            Write      : out std_logic;
            RFSH_n     : out std_logic;
            HALT_n     : out std_logic;
            BUSAK_n    : out std_logic;
            A          : out std_logic_vector(15 downto 0);
            DInst      : in  std_logic_vector(7 downto 0);
            DI         : in  std_logic_vector(7 downto 0);
            DO         : out std_logic_vector(7 downto 0);
            MC         : out std_logic_vector(2 downto 0);
            TS         : out std_logic_vector(2 downto 0);
            IntCycle_n : out std_logic;
            IntE       : out std_logic;
            Stop       : out std_logic;
            out0       : in  std_logic := '0';  -- 0 => OUT(C),0, 1 => OUT(C),255
            REG        : out std_logic_vector(211 downto 0); -- IFF2, IFF1, IM, IY, HL', DE', BC', IX, HL, DE, BC, PC, SP, R, I, F', A', F, A

            DIRSet     : in  std_logic := '0';
            DIR        : in  std_logic_vector(211 downto 0) := (others => '0') -- IFF2, IFF1, IM, IY, HL', DE', BC', IX, HL, DE, BC, PC, SP, R, I, F', A', F, A
        );
    end component;

	signal IntCycle_n	: std_logic;
	signal NoRead		: std_logic;
	signal Write		: std_logic;
	signal IORQ			: std_logic;
	signal DI_Reg		: std_logic_vector(7 downto 0);
	signal MCycle		: std_logic_vector(2 downto 0);
	signal TState		: std_logic_vector(2 downto 0);

begin

	u0 : T80
	generic map(
		Mode => Mode,
		IOWait => IOWait)
	port map(
		CEN => CEN,
		M1_n => M1_n,
		IORQ => IORQ,
		NoRead => NoRead,
		Write => Write,
		RFSH_n => RFSH_n,
		HALT_n => HALT_n,
		WAIT_n => Wait_n,
		INT_n => INT_n,
		NMI_n => NMI_n,
		RESET_n => RESET_n,
		BUSRQ_n => BUSRQ_n,
		BUSAK_n => BUSAK_n,
		CLK_n => CLK,
		A => A,
		DInst => DI,
		DI => DI_Reg,
		DO => DO,
		MC => MCycle,
		TS => TState,
		OUT0 => OUT0,
		IntCycle_n => IntCycle_n,
		REG => REG
	);

	process (RESET_n, CLK)
	begin
		if RESET_n = '0' then
			RD_n <= '1';
			WR_n <= '1';
			IORQ_n <= '1';
			MREQ_n <= '1';
			DI_Reg <= "00000000";
		elsif rising_edge(CLK) then
			if CEN = '1' then
				RD_n <= '1';
				WR_n <= '1';
				IORQ_n <= '1';
				MREQ_n <= '1';
				if MCycle = 1 then
					if TState = 1 or (TState = 2 and Wait_n = '0') then
						RD_n <= not IntCycle_n;
						MREQ_n <= not IntCycle_n;
						IORQ_n <= IntCycle_n;
					end if;
					if TState = 3 then
						MREQ_n <= '0';
					end if;
				else
					if (TState = 1 or (TState = 2 and Wait_n = '0')) and NoRead = '0' and Write = '0' then
						RD_n <= '0';
						IORQ_n <= not IORQ;
						MREQ_n <= IORQ;
					end if;
					if T2Write = 0 then
						if TState = 2 and Write = '1' then
							WR_n <= '0';
							IORQ_n <= not IORQ;
							MREQ_n <= IORQ;
						end if;
					else
						if (TState = 1 or (TState = 2 and Wait_n = '0')) and Write = '1' then
							WR_n <= '0';
							IORQ_n <= not IORQ;
							MREQ_n <= IORQ;
						end if;
					end if;
				end if;
				if TState = 2 and Wait_n = '1' then
					DI_Reg <= DI;
				end if;
			end if;
		end if;
	end process;
end;
