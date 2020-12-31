//============================================================================
//  DoDonPachi for MiSTer.
//  Copyright (c) 2019 Rick Wertenbroek
//  Copyright (c) 2020 Josh Bassett
//
//  This program is free software; you can redistribute it and/or modify it
//  under the terms of the GNU General Public License as published by the Free
//  Software Foundation; either version 2 of the License, or (at your option)
//  any later version.
//
//  This program is distributed in the hope that it will be useful, but WITHOUT
//  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
//  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
//  more details.
//
//  You should have received a copy of the GNU General Public License along
//  with this program; if not, write to the Free Software Foundation, Inc.,
//  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//============================================================================

module emu (
  //Master input clock
  input         CLK_50M,

  //Async reset from top-level module.
  //Can be used as initial reset.
  input         RESET,

  //Must be passed to hps_io module
  inout  [45:0] HPS_BUS,

  //Base video clock. Usually equals to CLK_SYS.
  output        CLK_VIDEO,

  //Multiple resolutions are supported using different CE_PIXEL rates.
  //Must be based on CLK_VIDEO
  output        CE_PIXEL,

  //Video aspect ratio for HDMI. Most retro systems have ratio 4:3.
  output  [7:0] VIDEO_ARX,
  output  [7:0] VIDEO_ARY,

  output  [7:0] VGA_R,
  output  [7:0] VGA_G,
  output  [7:0] VGA_B,
  output        VGA_HS,
  output        VGA_VS,
  output        VGA_DE,    // = ~(VBlank | HBlank)
  output        VGA_F1,
  output [1:0]  VGA_SL,
  output        VGA_SCALER, // Force VGA scaler

  // Use framebuffer from DDRAM (USE_FB=1 in qsf)
  // FB_FORMAT:
  //    [2:0] : 011=8bpp(palette) 100=16bpp 101=24bpp 110=32bpp
  //    [3]   : 0=16bits 565 1=16bits 1555
  //    [4]   : 0=RGB  1=BGR (for 16/24/32 modes)
  //
  // FB_STRIDE either 0 (rounded to 256 bytes) or multiple of 16 bytes.
  output        FB_EN,
  output  [4:0] FB_FORMAT,
  output [11:0] FB_WIDTH,
  output [11:0] FB_HEIGHT,
  output [31:0] FB_BASE,
  output [13:0] FB_STRIDE,
  input         FB_VBL,
  input         FB_LL,
  output        FB_FORCE_BLANK,

  // Palette control for 8bit modes.
  // Ignored for other video modes.
  output        FB_PAL_CLK,
  output  [7:0] FB_PAL_ADDR,
  output [23:0] FB_PAL_DOUT,
  input  [23:0] FB_PAL_DIN,
  output        FB_PAL_WR,

  output        LED_USER,  // 1 - ON, 0 - OFF.

  // b[1]: 0 - LED status is system status OR'd with b[0]
  //       1 - LED status is controled solely by b[0]
  // hint: supply 2'b00 to let the system control the LED.
  output  [1:0] LED_POWER,
  output  [1:0] LED_DISK,

  // I/O board button press simulation (active high)
  // b[1]: user button
  // b[0]: osd button
  output  [1:0] BUTTONS,

  input         CLK_AUDIO, // 24.576 MHz
  output [15:0] AUDIO_L,
  output [15:0] AUDIO_R,
  output        AUDIO_S,   // 1 - signed audio samples, 0 - unsigned
  output  [1:0] AUDIO_MIX, // 0 - no mix, 1 - 25%, 2 - 50%, 3 - 100% (mono)

  //High latency DDR3 RAM interface
  //Use for non-critical time purposes
  output        DDRAM_CLK,
  input         DDRAM_BUSY,
  output  [7:0] DDRAM_BURSTCNT,
  output [28:0] DDRAM_ADDR,
  input  [63:0] DDRAM_DOUT,
  input         DDRAM_DOUT_READY,
  output        DDRAM_RD,
  output [63:0] DDRAM_DIN,
  output  [7:0] DDRAM_BE,
  output        DDRAM_WE,

  //SDRAM interface with lower latency
  output        SDRAM_CLK,
  output        SDRAM_CKE,
  output [12:0] SDRAM_A,
  output  [1:0] SDRAM_BA,
  inout  [15:0] SDRAM_DQ,
  output        SDRAM_DQML,
  output        SDRAM_DQMH,
  output        SDRAM_nCS,
  output        SDRAM_nCAS,
  output        SDRAM_nRAS,
  output        SDRAM_nWE,

  // Open-drain User port.
  // 0 - D+/RX
  // 1 - D-/TX
  // 2..6 - USR2..USR6
  // Set USER_OUT to 1 to read from USER_IN.
  input   [6:0] USER_IN,
  output  [6:0] USER_OUT
);

assign VGA_F1 = 0;

assign AUDIO_S   = 1;
assign AUDIO_MIX = 0;

assign LED_DISK  = 0;
assign LED_POWER = 0;
assign BUTTONS = 0;

assign VIDEO_ARX = status[1] ? 8'd16 : status[2] ? 8'd3 : 8'd4;
assign VIDEO_ARY = status[1] ? 8'd9  : status[2] ? 8'd4 : 8'd3;

`include "build_id.v"
localparam CONF_STR = {
    "cave;;",
    "O1,Aspect Ratio,Original,Full Screen;",
    "O2,Orientation,Horz,Vert;",
    "O3,Flip Screen,Off,On;",
    "O46,Scandoubler Fx,None,HQ2x,CRT 25%,CRT 50%,CRT 75%;",
    "O7,Debug,Off,On;",
    "-;",
    "DIP;",
    "-;",
    "R0,Reset;",
    "J1,B0,B1,B2,Start,Coin,Pause,Service;",
    "V,v",`BUILD_DATE
};

////////////////////////////////////////////////////////////////////////////////
// CLOCKS
////////////////////////////////////////////////////////////////////////////////

wire clk_sys, clk_sdram, clk_video, clk_cpu;
wire locked;

pll pll (
    .refclk(CLK_50M),
    .rst(RESET),
    .outclk_0(clk_sys),
    .outclk_1(clk_sdram),
    .outclk_2(clk_video),
    .outclk_3(clk_cpu),
    .locked(locked)
);

assign DDRAM_CLK = clk_sys;
assign SDRAM_CLK = clk_sdram;

////////////////////////////////////////////////////////////////////////////////
// HPS IO
////////////////////////////////////////////////////////////////////////////////

wire  [1:0] buttons;
wire [31:0] status;
wire        forced_scandoubler;
wire [21:0] gamma_bus;
wire        direct_video;
wire        debug;

wire [24:0] ioctl_addr;
wire [15:0] ioctl_dout;
wire        ioctl_wr;
wire        ioctl_wait;
wire        ioctl_download;
wire  [7:0] ioctl_index;

wire [10:0] ps2_key;
wire  [10:0] joystick_0, joystick_1;

hps_io #(.STRLEN($size(CONF_STR)>>3), .WIDE(1)) hps_io (
    .clk_sys(clk_sys),
    .HPS_BUS(HPS_BUS),

    .conf_str(CONF_STR),

    .buttons(buttons),
    .status(status),
    .status_menumask(direct_video),
    .forced_scandoubler(forced_scandoubler),
    .gamma_bus(gamma_bus),
    .direct_video(direct_video),

    .ioctl_addr(ioctl_addr),
    .ioctl_dout(ioctl_dout),
    .ioctl_wr(ioctl_wr),
    .ioctl_wait(ioctl_wait),
    .ioctl_download(ioctl_download),
    .ioctl_index(ioctl_index),

    .joystick_0(joystick_0),
    .joystick_1(joystick_1),

    .ps2_key(ps2_key)
);

assign debug = status[7];

////////////////////////////////////////////////////////////////////////////////
// VIDEO
////////////////////////////////////////////////////////////////////////////////

wire [7:0] r, g, b;
wire hsync, vsync;
wire hblank, vblank;
wire video_enable;
wire [2:0] fx = status[6:4];
wire [2:0] sl = fx ? fx - 1'd1 : 3'd0;

assign CLK_VIDEO = clk_video;
assign CE_PIXEL = '1;
assign VGA_DE = video_enable;
assign VGA_HS = hsync;
assign VGA_VS = vsync;
assign VGA_R  = r;
assign VGA_G  = g;
assign VGA_B  = b;
assign VGA_SL = sl[1:0];

////////////////////////////////////////////////////////////////////////////////
// CONTROLS
////////////////////////////////////////////////////////////////////////////////

wire       pressed = ps2_key[9];
wire [7:0] code    = ps2_key[7:0];

reg key_left  = 0;
reg key_right = 0;
reg key_down  = 0;
reg key_up    = 0;
reg key_ctrl  = 0;
reg key_alt   = 0;
reg key_space = 0;
reg key_1     = 0;
reg key_2     = 0;
reg key_5     = 0;
reg key_6     = 0;
reg key_9     = 0;
reg key_0     = 0;
reg key_a     = 0;
reg key_s     = 0;
reg key_q     = 0;
reg key_r     = 0;
reg key_f     = 0;
reg key_d     = 0;
reg key_g     = 0;
reg key_p     = 0;

always @(posedge clk_sys) begin
reg old_state;
old_state <= ps2_key[10];

if (old_state != ps2_key[10]) begin
case (code)
  'h75: key_up    <= pressed;
  'h72: key_down  <= pressed;
  'h6B: key_left  <= pressed;
  'h74: key_right <= pressed;
  'h14: key_ctrl  <= pressed;
  'h11: key_alt   <= pressed;
  'h29: key_space <= pressed;
  'h16: key_1     <= pressed;
  'h1E: key_2     <= pressed;
  'h2E: key_5     <= pressed;
  'h36: key_6     <= pressed;
  'h46: key_9     <= pressed;
  'h45: key_0     <= pressed;
  'h1C: key_a     <= pressed;
  'h1B: key_s     <= pressed;
  'h15: key_q     <= pressed;
  'h2D: key_r     <= pressed;
  'h2B: key_f     <= pressed;
  'h23: key_d     <= pressed;
  'h34: key_g     <= pressed;
  'h4d: key_p     <= pressed;
endcase
end
end

wire player_1_up       = key_up    | joystick_0[3];
wire player_1_down     = key_down  | joystick_0[2];
wire player_1_left     = key_left  | joystick_0[1];
wire player_1_right    = key_right | joystick_0[0];
wire player_1_button_1 = key_ctrl  | joystick_0[4];
wire player_1_button_2 = key_alt   | joystick_0[5];
wire player_1_button_3 = key_space | joystick_0[6];
wire player_1_start    = key_1     | joystick_0[7];
wire player_1_coin     = key_5     | joystick_0[8];
wire player_1_pause    = key_p     | joystick_0[9];
wire player_1_service  = key_9     | joystick_0[10];
wire player_2_up       = key_r     | joystick_1[3];
wire player_2_down     = key_f     | joystick_1[2];
wire player_2_left     = key_d     | joystick_1[1];
wire player_2_right    = key_g     | joystick_1[0];
wire player_2_button_1 = key_a     | joystick_1[4];
wire player_2_button_2 = key_s     | joystick_1[5];
wire player_2_button_3 = key_q     | joystick_1[6];
wire player_2_start    = key_2     | joystick_1[7];
wire player_2_coin     = key_6     | joystick_1[8];
wire player_2_pause    =             joystick_0[9];
wire player_2_service  = key_0     | joystick_1[10];

////////////////////////////////////////////////////////////////////////////////
// CAVE
////////////////////////////////////////////////////////////////////////////////

wire reset_sys = RESET | ~locked;
wire reset_video = RESET | ~locked;
wire reset_cpu = RESET | status[0] | buttons[1] | ~locked;

logic reset_sys_0;
logic reset_sys_1;
logic reset_sys_2;

always_ff @(posedge clk_sys) begin
    reset_sys_0 <= reset_sys;
    reset_sys_1 <= reset_sys_0;
    reset_sys_2 <= reset_sys_1;
end

logic reset_video_0;
logic reset_video_1;
logic reset_video_2;

always_ff @(posedge clk_video) begin
    reset_video_0 <= reset_video;
    reset_video_1 <= reset_video_0;
    reset_video_2 <= reset_video_1;
end

logic reset_cpu_0;
logic reset_cpu_1;
logic reset_cpu_2;

always_ff @(posedge clk_cpu) begin
    reset_cpu_0 <= reset_cpu;
    reset_cpu_1 <= reset_cpu_0;
    reset_cpu_2 <= reset_cpu_1;
end

wire [31:0] ddr_addr;
wire        sdram_oe;
wire [15:0] sdram_din;
wire [15:0] sdram_dout;

assign DDRAM_ADDR = ddr_addr[31:3];
assign SDRAM_DQ = sdram_oe ? sdram_din : 16'bZ;
assign sdram_dout = SDRAM_DQ;

Main main (
    // Fast clock domain
    .clock(clk_sys),
    .reset(reset_sys_2),
    // Video clock domain
    .io_videoClock(clk_video),
    .io_videoReset(reset_video_2),
    // CPU clock domain
    .io_cpuClock(clk_cpu),
    .io_cpuReset(reset_cpu_2),
    // Rotate
    .io_rotate(status[2]),
    // Player input signals
    .io_player_player1({player_1_service, player_1_coin, player_1_start, player_1_button_3, player_1_button_2, player_1_button_1, player_1_right, player_1_left, player_1_down, player_1_up}),
    .io_player_player2({player_2_service, layer_2_coin, player_2_start, player_2_button_3, player_2_button_2, player_2_button_1, player_2_right, player_2_left, player_2_down, player_2_up}),
    .io_player_pause(player_1_pause | player_2_pause),
    // Video signals
    .io_video_hSync(hsync),
    .io_video_vSync(vsync),
    .io_video_hBlank(hblank),
    .io_video_vBlank(vblank),
    .io_video_enable(video_enable),
    // Frame buffer signals
    .io_frameBuffer_enable(FB_EN),
    .io_frameBuffer_hSize(FB_WIDTH),
    .io_frameBuffer_vSize(FB_HEIGHT),
    .io_frameBuffer_format(FB_FORMAT),
    .io_frameBuffer_base(FB_BASE),
    .io_frameBuffer_stride(FB_STRIDE),
    .io_frameBuffer_vBlank(FB_VBL),
    .io_frameBuffer_lowLat(FB_LL),
    .io_frameBuffer_forceBlank(FB_FORCE_BLANK),
    // DDR
    .io_ddr_rd(DDRAM_RD),
    .io_ddr_wr(DDRAM_WE),
    .io_ddr_addr(ddr_addr),
    .io_ddr_mask(DDRAM_BE),
    .io_ddr_din(DDRAM_DIN),
    .io_ddr_dout(DDRAM_DOUT),
    .io_ddr_waitReq(DDRAM_BUSY),
    .io_ddr_valid(DDRAM_DOUT_READY),
    .io_ddr_burstLength(DDRAM_BURSTCNT),
    // SDRAM
    .io_sdram_cke(SDRAM_CKE),
    .io_sdram_cs_n(SDRAM_nCS),
    .io_sdram_ras_n(SDRAM_nRAS),
    .io_sdram_cas_n(SDRAM_nCAS),
    .io_sdram_we_n(SDRAM_nWE),
    .io_sdram_oe(sdram_oe),
    .io_sdram_bank(SDRAM_BA),
    .io_sdram_addr(SDRAM_A),
    .io_sdram_din(sdram_din),
    .io_sdram_dout(sdram_dout),
    // Download
    .io_download_cs(ioctl_download),
    .io_download_wr(ioctl_wr),
    .io_download_waitReq(ioctl_wait),
    .io_download_addr(ioctl_addr),
    .io_download_dout(ioctl_dout),
    // RGB output
    .io_rgb_r(r),
    .io_rgb_g(g),
    .io_rgb_b(b),
    // Audio output
    .io_audio_left(AUDIO_L),
    .io_audio_right(AUDIO_R)
);

endmodule
