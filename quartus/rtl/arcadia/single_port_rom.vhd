--   __   __     __  __     __         __
--  /\ "-.\ \   /\ \/\ \   /\ \       /\ \
--  \ \ \-.  \  \ \ \_\ \  \ \ \____  \ \ \____
--   \ \_\\"\_\  \ \_____\  \ \_____\  \ \_____\
--    \/_/ \/_/   \/_____/   \/_____/   \/_____/
--   ______     ______       __     ______     ______     ______
--  /\  __ \   /\  == \     /\ \   /\  ___\   /\  ___\   /\__  _\
--  \ \ \/\ \  \ \  __<    _\_\ \  \ \  __\   \ \ \____  \/_/\ \/
--   \ \_____\  \ \_____\ /\_____\  \ \_____\  \ \_____\    \ \_\
--    \/_____/   \/_____/ \/_____/   \/_____/   \/_____/     \/_/
--
-- https://joshbassett.info
-- https://twitter.com/nullobject
-- https://github.com/nullobject
--
-- Copyright (c) 2020 Josh Bassett
--
-- Permission is hereby granted, free of charge, to any person obtaining a copy
-- of this software and associated documentation files (the "Software"), to deal
-- in the Software without restriction, including without limitation the rights
-- to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
-- copies of the Software, and to permit persons to whom the Software is
-- furnished to do so, subject to the following conditions:
--
-- The above copyright notice and this permission notice shall be included in all
-- copies or substantial portions of the Software.
--
-- THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
-- IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
-- FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
-- AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
-- LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
-- OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
-- SOFTWARE.

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

library altera_mf;
use altera_mf.altera_mf_components.all;

entity single_port_rom is
  generic (
    ADDR_WIDTH : natural;
    DATA_WIDTH : natural;
    DEPTH      : natural;
    INIT_FILE  : string
  );
  port (
    clk  : in std_logic;
    rd   : in std_logic := '1';
    addr : in unsigned(ADDR_WIDTH-1 downto 0);
    dout : out std_logic_vector(DATA_WIDTH-1 downto 0)
  );
end single_port_rom;

architecture arch of single_port_rom is
begin
  altsyncram_component : altsyncram
  generic map (
    address_aclr_a         => "NONE",
    clock_enable_input_a   => "BYPASS",
    clock_enable_output_a  => "BYPASS",
    init_file              => INIT_FILE,
    intended_device_family => "Cyclone V",
    lpm_hint               => "ENABLE_RUNTIME_MOD=NO",
    lpm_type               => "altsyncram",
    numwords_a             => DEPTH,
    operation_mode         => "ROM",
    outdata_aclr_a         => "NONE",
    outdata_reg_a          => "UNREGISTERED",
    width_a                => DATA_WIDTH,
    width_byteena_a        => 1,
    widthad_a              => ADDR_WIDTH
  )
  port map (
    address_a => std_logic_vector(addr),
    clock0    => clk,
    rden_a    => rd,
    q_a       => dout
  );
end architecture arch;
