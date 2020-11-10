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

entity true_dual_port_ram is
  generic (
    ADDR_WIDTH_A : natural := 8;
    ADDR_WIDTH_B : natural := 8;
    DATA_WIDTH_A : natural := 8;
    DATA_WIDTH_B : natural := 8;
    DEPTH_A      : natural := 0;
    DEPTH_B      : natural := 0
  );
  port (
    -- Port A
    clk_a  : in std_logic;
    cs_a   : in std_logic := '1';
    wr_a   : in std_logic := '0';
    rd_a   : in std_logic := '1';
    ack_a  : out std_logic;
    addr_a : in unsigned(ADDR_WIDTH_A-1 downto 0);
    mask_a : in std_logic_vector((DATA_WIDTH_A/8)-1 downto 0) := (others => '1');
    din_a  : in std_logic_vector(DATA_WIDTH_A-1 downto 0) := (others => '0');
    dout_a : out std_logic_vector(DATA_WIDTH_A-1 downto 0);

    -- Port B
    clk_b  : in std_logic;
    cs_b   : in std_logic := '1';
    wr_b   : in std_logic := '0';
    rd_b   : in std_logic := '1';
    ack_b  : out std_logic;
    addr_b : in unsigned(ADDR_WIDTH_B-1 downto 0);
    din_b  : in std_logic_vector(DATA_WIDTH_B-1 downto 0) := (others => '0');
    dout_b : out std_logic_vector(DATA_WIDTH_B-1 downto 0)
  );
end true_dual_port_ram;

architecture arch of true_dual_port_ram is
  -- Returns the number of words for the given depth, otherwise it defaults to
  -- the full address range
  function num_words(depth, addr_width : natural) return natural is
  begin
    if depth > 0 then
      return depth;
    else
      return 2**addr_width;
    end if;
  end function num_words;

  signal q_a : std_logic_vector(DATA_WIDTH_A-1 downto 0);
  signal q_b : std_logic_vector(DATA_WIDTH_B-1 downto 0);
begin
  altsyncram_component : altsyncram
  generic map (
    address_reg_b                 => "CLOCK1",
    byte_size                     => 8,
    clock_enable_input_a          => "BYPASS",
    clock_enable_input_b          => "BYPASS",
    clock_enable_output_a         => "BYPASS",
    clock_enable_output_b         => "BYPASS",
    indata_reg_b                  => "CLOCK1",
    intended_device_family        => "Cyclone V",
    lpm_type                      => "altsyncram",
    numwords_a                    => num_words(DEPTH_A, ADDR_WIDTH_A),
    numwords_b                    => num_words(DEPTH_B, ADDR_WIDTH_B),
    operation_mode                => "BIDIR_DUAL_PORT",
    outdata_aclr_a                => "NONE",
    outdata_aclr_b                => "NONE",
    outdata_reg_a                 => "UNREGISTERED",
    outdata_reg_b                 => "UNREGISTERED",
    power_up_uninitialized        => "FALSE",
    read_during_write_mode_port_a => "NEW_DATA_NO_NBE_READ",
    read_during_write_mode_port_b => "NEW_DATA_NO_NBE_READ",
    width_a                       => DATA_WIDTH_A,
    width_b                       => DATA_WIDTH_B,
    width_byteena_a               => DATA_WIDTH_A/8,
    widthad_a                     => ADDR_WIDTH_A,
    widthad_b                     => ADDR_WIDTH_B,
    wrcontrol_wraddress_reg_b     => "CLOCK1"
  )
  port map (
    address_a => std_logic_vector(addr_a),
    address_b => std_logic_vector(addr_b),
    clock0    => clk_a,
    clock1    => clk_b,
    data_a    => din_a,
    data_b    => din_b,
    byteena_a => mask_a,
    rden_a    => cs_a and rd_a,
    rden_b    => cs_b and rd_b,
    wren_a    => cs_a and wr_a,
    wren_b    => cs_b and wr_b,
    q_a       => q_a,
    q_b       => q_b
  );

  -- The ACK_A signal will be asserted after a read or write operation on port A
  process(clk_a)
  begin
    if rising_edge(clk_a) then
      ack_a <= cs_a and (rd_a or wr_a);
    end if;
  end process;

  -- The ACK_B signal will be asserted after a read or write operation on port B
  process(clk_b)
  begin
    if rising_edge(clk_b) then
      ack_b <= cs_b and (rd_b or wr_b);
    end if;
  end process;

  -- Outputs
  dout_a <= q_a when cs_a = '1' else (others => '0');
  dout_b <= q_b when cs_b = '1' else (others => '0');
end architecture arch;
