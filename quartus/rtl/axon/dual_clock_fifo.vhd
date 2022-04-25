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
use ieee.math_real.all;

library altera_mf;
use altera_mf.altera_mf_components.all;

entity dual_clock_fifo is
  generic (
    DATA_WIDTH : natural;
    DEPTH      : natural
  );
  port (
    data    : in std_logic_vector(DATA_WIDTH-1 downto 0);
    rdclk   : in std_logic;
    rdreq   : in std_logic;
    wrclk   : in std_logic;
    wrreq   : in std_logic;
    q       : out std_logic_vector(DATA_WIDTH-1 downto 0);
    rdempty : out std_logic;
    wrfull  : out std_logic
  );
end dual_clock_fifo;

architecture arch of dual_clock_fifo is
begin
  dcfifo_component : dcfifo
  generic map (
    intended_device_family => "Cyclone V",
    lpm_numwords           => DEPTH,
    lpm_showahead          => "ON",
    lpm_type               => "dcfifo",
    lpm_width              => DATA_WIDTH,
    lpm_widthu             => positive(ceil(log2(real(DEPTH)))),
    overflow_checking      => "ON",
    rdsync_delaypipe       => 4,
    underflow_checking     => "ON",
    use_eab                => "ON",
    wrsync_delaypipe       => 4
  )
  port map (
    data    => data,
    rdclk   => rdclk,
    rdreq   => rdreq,
    wrclk   => wrclk,
    wrreq   => wrreq,
    q       => q,
    rdempty => rdempty,
    wrfull  => wrfull
  );
end arch;
