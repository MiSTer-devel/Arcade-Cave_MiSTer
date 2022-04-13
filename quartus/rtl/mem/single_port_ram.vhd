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

entity single_port_ram is
  generic (
    ADDR_WIDTH  : natural := 8;
    DATA_WIDTH  : natural := 8;
    DEPTH       : natural := 0;
    MASK_ENABLE : boolean := false
  );
  port (
    clk  : in std_logic;
    rd   : in std_logic := '1';
    wr   : in std_logic := '0';
    addr : in unsigned(ADDR_WIDTH-1 downto 0);
    mask : in std_logic_vector(DATA_WIDTH/8-1 downto 0) := (others => '1');
    din  : in std_logic_vector(DATA_WIDTH-1 downto 0) := (others => '0');
    dout : out std_logic_vector(DATA_WIDTH-1 downto 0)
  );
end single_port_ram;

architecture arch of single_port_ram is
  -- returns the number of words for the given depth, otherwise it defaults to the full address range
  function num_words(depth, addr_width : natural) return natural is
  begin
    if depth > 0 then
      return depth;
    else
      return 2**addr_width;
    end if;
  end function num_words;
begin
  masked_ram_generate : if MASK_ENABLE generate
    altsyncram_component : altsyncram
    generic map (
      byte_size                     => 8,
      clock_enable_input_a          => "BYPASS",
      clock_enable_output_a         => "BYPASS",
      intended_device_family        => "Cyclone V",
      lpm_hint                      => "ENABLE_RUNTIME_MOD=NO",
      lpm_type                      => "altsyncram",
      numwords_a                    => num_words(DEPTH, ADDR_WIDTH),
      operation_mode                => "SINGLE_PORT",
      outdata_aclr_a                => "NONE",
      outdata_reg_a                 => "UNREGISTERED",
      power_up_uninitialized        => "FALSE",
      read_during_write_mode_port_a => "NEW_DATA_NO_NBE_READ",
      width_a                       => DATA_WIDTH,
      width_byteena_a               => DATA_WIDTH/8,
      widthad_a                     => ADDR_WIDTH
    )
    port map (
      address_a => std_logic_vector(addr),
      clock0    => clk,
      data_a    => din,
      byteena_a => mask,
      rden_a    => rd,
      wren_a    => wr,
      q_a       => dout
    );
  else generate
    altsyncram_component : altsyncram
    generic map (
      clock_enable_input_a          => "BYPASS",
      clock_enable_output_a         => "BYPASS",
      intended_device_family        => "Cyclone V",
      lpm_hint                      => "ENABLE_RUNTIME_MOD=NO",
      lpm_type                      => "altsyncram",
      numwords_a                    => num_words(DEPTH, ADDR_WIDTH),
      operation_mode                => "SINGLE_PORT",
      outdata_aclr_a                => "NONE",
      outdata_reg_a                 => "UNREGISTERED",
      power_up_uninitialized        => "FALSE",
      read_during_write_mode_port_a => "NEW_DATA_NO_NBE_READ",
      width_a                       => DATA_WIDTH,
      width_byteena_a               => 1,
      widthad_a                     => ADDR_WIDTH
    )
    port map (
      address_a => std_logic_vector(addr),
      clock0 => clk,
      data_a => din,
      rden_a => rd,
      wren_a => wr,
      q_a    => dout
    );
  end generate;
end architecture arch;
