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

use work.cave_pkg.all;

-- This module generates the video timing signals for a CRT.
--
-- The sync signals tell the CRT when to start and stop scanning. The
-- horizontal sync tells the CRT when to start a new scanline, and the vertical
-- sync tells it when to start a new field.
--
-- The blanking signals indicate whether the beam is in the horizontal or
-- vertical blanking (non-visible) regions. Video output should be disabled
-- while the beam is in these regions, they are also used to do things like
-- fetch graphics data.
entity video_timing is
  generic (
    constant H_DISPLAY     : natural;
    constant H_FRONT_PORCH : natural;
    constant H_RETRACE     : natural;
    constant H_BACK_PORCH  : natural;
    constant V_DISPLAY     : natural;
    constant V_FRONT_PORCH : natural;
    constant V_RETRACE     : natural;
    constant V_BACK_PORCH  : natural
  );
  port (
    clk   : in std_logic;
    video : out video_t
  );
end video_timing;

architecture arch of video_timing is
  -- Horizontal regions
  constant H_END_SCAN      : natural := H_BACK_PORCH+H_DISPLAY+H_FRONT_PORCH+H_RETRACE;
  constant H_BEGIN_SYNC    : natural := H_BACK_PORCH+H_DISPLAY+H_FRONT_PORCH;
  constant H_END_SYNC      : natural := H_BACK_PORCH+H_DISPLAY+H_FRONT_PORCH+H_RETRACE;
  constant H_BEGIN_DISPLAY : natural := H_BACK_PORCH;
  constant H_END_DISPLAY   : natural := H_BACK_PORCH+H_DISPLAY;

  -- Vertical regions
  constant V_END_SCAN      : natural := V_BACK_PORCH+V_DISPLAY+V_FRONT_PORCH+V_RETRACE;
  constant V_BEGIN_SYNC    : natural := V_BACK_PORCH+V_DISPLAY+V_FRONT_PORCH;
  constant V_END_SYNC      : natural := V_BACK_PORCH+V_DISPLAY+V_FRONT_PORCH+V_RETRACE;
  constant V_BEGIN_DISPLAY : natural := V_BACK_PORCH;
  constant V_END_DISPLAY   : natural := V_BACK_PORCH+V_DISPLAY;

  -- Position counters
  signal x : natural range 0 to 511;
  signal y : natural range 0 to 511;

  -- Sync signals
  signal hsync, vsync : std_logic;

  -- Display signals
  signal hdisplay, vdisplay : std_logic;

  -- Position
  signal pos : vec2_t;
begin
  update_position_counters : process (clk)
  begin
    if rising_edge(clk) then
      if x = H_END_SCAN-1 then
        x <= 0;
        if y = V_END_SCAN-1 then
          y <= 0;
        else
          y <= y + 1;
        end if;
      else
        x <= x + 1;
      end if;
    end if;
  end process;

  -- Offset the position so the display region begins at the origin
  pos.x <= to_unsigned(x-H_BACK_PORCH, video.pos.x'length);
  pos.y <= to_unsigned(y-V_BACK_PORCH, video.pos.y'length);

  -- Sync signals
  hsync <= '1' when x >= H_BEGIN_SYNC and x < H_END_SYNC else '0';
  vsync <= '1' when y >= V_BEGIN_SYNC and y < V_END_SYNC else '0';

  -- Display signals
  hdisplay <= '1' when x >= H_BEGIN_DISPLAY and x < H_END_DISPLAY else '0';
  vdisplay <= '1' when y >= V_BEGIN_DISPLAY and y < V_END_DISPLAY else '0';

  -- Outputs
  video.pos    <= pos;
  video.hsync  <= hsync;
  video.vsync  <= vsync;
  video.hblank <= not hdisplay;
  video.vblank <= not vdisplay;
  video.enable <= hdisplay and vdisplay;
end architecture arch;
