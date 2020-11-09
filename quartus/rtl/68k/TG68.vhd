------------------------------------------------------------------------------
------------------------------------------------------------------------------
--                                                                          --
-- This is the TOP-Level for TG68_fast to generate 68K Bus signals          --
--                                                                          --
-- Copyright (c) 2007-2008 Tobias Gubener <tobiflex@opencores.org>          --
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
--
-- Revision 1.02 2008/01/23
-- bugfix Timing
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
-- Add FDC Output
-- add odd Address test
-- add TRACE
-- Movem with regmask==x0000



library ieee;
use ieee.std_logic_1164.all;
use ieee.std_logic_unsigned.all;

entity TG68 is
    port(
        clk        : in  std_logic;
        reset      : in  std_logic;
        clkena_in  : in  std_logic := '1';
        data_in    : in  std_logic_vector(15 downto 0);
        IPL        : in  std_logic_vector(2 downto 0) := "111";
        dtack      : in  std_logic;
        addr       : out std_logic_vector(31 downto 0);
        data_out   : out std_logic_vector(15 downto 0);
        as         : out std_logic;
        uds        : out std_logic;
        lds        : out std_logic;
        rw         : out std_logic;
        drive_data : out std_logic;      --enable for data_out driver
        -- Added debug signals
        TG68_PC_o  : out    std_logic_vector(31 downto 0);
        TG68_PCW_o : out    std_logic
        );
end TG68;

architecture logic of TG68 is

    component TG68_fast
        port (
            clk        : in     std_logic;
            reset      : in     std_logic;
            clkena_in  : in     std_logic;
            data_in    : in     std_logic_vector(15 downto 0);
            IPL        : in     std_logic_vector(2 downto 0);
            test_IPL   : in     std_logic;
            address    : out    std_logic_vector(31 downto 0);
            data_write : out    std_logic_vector(15 downto 0);
            state_out  : out    std_logic_vector(1 downto 0);
            decodeOPC  : buffer std_logic;
            wr         : out    std_logic;
            UDS, LDS   : out    std_logic;
            -- Added debug signals
            TG68_PC_o  : out    std_logic_vector(31 downto 0);
            TG68_PCW_o : out    std_logic
            );
    end component;


    signal as_s     : std_logic;
    signal as_e     : std_logic;
    signal uds_s    : std_logic;
    signal uds_e    : std_logic;
    signal lds_s    : std_logic;
    signal lds_e    : std_logic;
    signal rw_s     : std_logic;
    signal rw_e     : std_logic;
    signal waitm    : std_logic;
    signal clkena_e : std_logic;
    signal S_state  : std_logic_vector(1 downto 0);
    signal decode   : std_logic;
    signal wr       : std_logic;
    signal uds_in   : std_logic;
    signal lds_in   : std_logic;
    signal state    : std_logic_vector(1 downto 0);
    signal clkena   : std_logic;
    signal n_clk    : std_logic;
    signal cpuIPL   : std_logic_vector(2 downto 0);


begin

    n_clk <= not clk;

    TG68_fast_inst : TG68_fast
        port map (
            clk        => n_clk,        -- : in std_logic;
            reset      => reset,        -- : in std_logic;
            clkena_in  => clkena,       -- : in std_logic;
            data_in    => data_in,      -- : in std_logic_vector(15 downto 0);
            IPL        => cpuIPL,       -- : in std_logic_vector(2 downto 0);
            test_IPL   => '0',          -- : in std_logic;
            address    => addr,         -- : out std_logic_vector(31 downto 0);
            data_write => data_out,     -- : out std_logic_vector(15 downto 0);
            state_out  => state,        -- : out std_logic_vector(1 downto 0);
            decodeOPC  => decode,       -- : buffer std_logic;
            wr         => wr,           -- : out std_logic;
            UDS        => uds_in,       -- : out std_logic;
            LDS        => lds_in,       -- : out std_logic;
            TG68_PC_o  => TG68_PC_o,
            TG68_PCW_o => TG68_PCW_o
            );

    process (clk)
    begin
        if clkena_in = '1' and (clkena_e = '1' or state = "01") then
            clkena <= '1';
        else
            clkena <= '0';
        end if;
    end process;

    process (clk, reset, state, as_s, as_e, rw_s, rw_e, uds_s, uds_e, lds_s, lds_e)
    begin
        if state = "01" then
            as  <= '1';
            rw  <= '1';
            uds <= '1';
            lds <= '1';
        else
            as  <= as_s and as_e;
            rw  <= rw_s and rw_e;
            uds <= uds_s and uds_e;
            lds <= lds_s and lds_e;
        end if;
        if reset = '0' then
            S_state <= "11";
            as_s    <= '1';
            rw_s    <= '1';
            uds_s   <= '1';
            lds_s   <= '1';
        elsif rising_edge(clk) then
            if clkena_in = '1' then
                as_s  <= '1';
                rw_s  <= '1';
                uds_s <= '1';
                lds_s <= '1';
                if state /= "01" or decode = '1' then
                    case S_state is
                        when "00" => as_s <= '0';
                                     rw_s <= wr;
                                     if wr = '1' then
                                         uds_s <= uds_in;
                                         lds_s <= lds_in;
                                     end if;
                                     S_state <= "01";
                        when "01" => as_s <= '0';
                                     rw_s    <= wr;
                                     uds_s   <= uds_in;
                                     lds_s   <= lds_in;
                                     S_state <= "10";
                        when "10" =>
                            rw_s <= wr;
                            if waitm = '0' then
                                S_state <= "11";
                            end if;
                        when "11" =>
                            S_state <= "00";
                        when others => null;
                    end case;
                end if;
            end if;
        end if;
        if reset = '0' then
            as_e       <= '1';
            rw_e       <= '1';
            uds_e      <= '1';
            lds_e      <= '1';
            clkena_e   <= '0';
            cpuIPL     <= "111";
            drive_data <= '0';
        elsif falling_edge(clk) then
            if clkena_in = '1' then
                as_e       <= '1';
                rw_e       <= '1';
                uds_e      <= '1';
                lds_e      <= '1';
                clkena_e   <= '0';
                drive_data <= '0';
                case S_state is
                    when "00" => null;
                    when "01" => drive_data <= not wr;
                    when "10" => as_e       <= '0';
                                 uds_e      <= uds_in;
                                 lds_e      <= lds_in;
                                 cpuIPL     <= IPL;
                                 drive_data <= not wr;
                                 if state = "01" then
                                     clkena_e <= '1';
                                     waitm    <= '0';
                                 else
                                     clkena_e <= not dtack;
                                     waitm    <= dtack;
                                 end if;
                    when others => null;
                end case;
            end if;
        end if;
    end process;
end;
