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
  inout  [48:0] HPS_BUS,

  //Base video clock. Usually equals to CLK_SYS.
  output        CLK_VIDEO,

  //Multiple resolutions are supported using different CE_PIXEL rates.
  //Must be based on CLK_VIDEO
  output        CE_PIXEL,

  //Video aspect ratio for HDMI. Most retro systems have ratio 4:3.
  output  [12:0] VIDEO_ARX,
  output  [12:0] VIDEO_ARY,

  output  [7:0] VGA_R,
  output  [7:0] VGA_G,
  output  [7:0] VGA_B,
  output        VGA_HS,
  output        VGA_VS,
  output        VGA_DE,    // = ~(VBlank | HBlank)
  output        VGA_F1,
  output [1:0]  VGA_SL,
  output        VGA_SCALER, // Force VGA scaler

  input  [11:0] HDMI_WIDTH,
  input  [11:0] HDMI_HEIGHT,
  output        HDMI_FREEZE,

  // Use framebuffer in DDRAM (USE_FB=1 in qsf)
  // FB_FORMAT:
  //    [2:0] : 011=8bpp(palette) 100=16bpp 101=24bpp 110=32bpp
  //    [3]   : 0=16bits 565 1=16bits 1555
  //    [4]   : 0=RGB  1=BGR (for 16/24/32 modes)
  //
  // FB_STRIDE either 0 (rounded to 256 bytes) or multiple of pixel size (in bytes)
  output        FB_EN,
  output  [4:0] FB_FORMAT,
  output [11:0] FB_WIDTH,
  output [11:0] FB_HEIGHT,
  output [31:0] FB_BASE,
  output [13:0] FB_STRIDE,
  input         FB_VBL,
  input         FB_LL,
  output        FB_FORCE_BLANK,

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

  //ADC
  inout   [3:0] ADC_BUS,

  //SD-SPI
  output        SD_SCK,
  output        SD_MOSI,
  input         SD_MISO,
  output        SD_CS,
  input         SD_CD,

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

  input         UART_CTS,
  output        UART_RTS,
  input         UART_RXD,
  output        UART_TXD,
  output        UART_DTR,
  input         UART_DSR,

  // Open-drain User port.
  // 0 - D+/RX
  // 1 - D-/TX
  // 2..6 - USR2..USR6
  // Set USER_OUT to 1 to read from USER_IN.
  input   [6:0] USER_IN,
  output  [6:0] USER_OUT,

  input         OSD_STATUS
);

assign ADC_BUS  = 'Z;
assign USER_OUT = '1;
assign {UART_RTS, UART_TXD, UART_DTR} = 0;
assign {SD_SCK, SD_MOSI, SD_CS} = 'Z;

assign AUDIO_S   = 1;
assign AUDIO_MIX = 0;

assign BUTTONS = 0;

assign VIDEO_ARX = status[1] ? 12'd16 : status[2] ? 12'd3 : 12'd4;
assign VIDEO_ARY = status[1] ? 12'd9  : status[2] ? 12'd4 : 12'd3;

`include "build_id.v"
localparam CONF_STR = {
  "cave;;",
  "OOR,CRT H adjust,0,+1,+2,+3,+4,+5,+6,+7,-8,-7,-6,-5,-4,-3,-2,-1;",
  "OSV,CRT V adjust,0,+1,+2,+3,+4,+5,+6,+7,-8,-7,-6,-5,-4,-3,-2,-1;",
  "D0O1,Aspect Ratio,4:3,16:9;",
  "D0O2,Orientation,Horizontal,Vertical;",
  "O3,Flip Screen,Off,On;",
  "O46,Scandoubler,None,HQ2x,CRT 25%,CRT 50%,CRT 75%;",
  "-;",
  "P1,Advanced;",
  "P1-;",
  "P1O7,Refresh Rate,57Hz,60Hz;",
  "P2,Debug;",
  "P2-;",
  "P2OA,Sprites,On,Off;",
  "P2OB,Layer 0,On,Off;",
  "P2OC,Layer 1,On,Off;",
  "P2OD,Layer 2,On,Off;",
  "P2OE,Row Scroll,On,Off;",
  "P2OF,Row Select,On,Off;",
  "P2OGJ,PCB,Dangun Feveron,DoDonPachi,DonPachi,ESP Ra.De.,Puzzle Uo Poko,Guwange;",
  "-;",
  "R0,Reset;",
  "J,B0,B1,B2,Start,Coin,Pause,Service;",
  "V,v",`BUILD_DATE," - Made with love by nullobject.;"
};

////////////////////////////////////////////////////////////////////////////////
// CLOCK AND RESET
////////////////////////////////////////////////////////////////////////////////

wire pll_sys_locked, pll_video_locked;
wire clk_sys, clk_sdram, clk_video;
wire rst_sys, rst_video, rst_cpu;
reg  rst_pll;

// Resets the PLL if it loses lock
always @(posedge clk_sys or posedge RESET) begin
  reg old_locked;
  reg [7:0] rst_cnt;

  if (RESET) begin
    rst_pll <= 0;
    rst_cnt <= 8'h00;
  end else begin
    old_locked <= pll_sys_locked;
    if (old_locked && !pll_sys_locked) begin
      rst_cnt <= 8'hff; // keep reset high for 256 cycles
      rst_pll <= 1;
    end else begin
      if (rst_cnt != 8'h00)
        rst_cnt <= rst_cnt - 8'h1;
      else
        rst_pll <= 0;
    end
  end
end

pll_sys pll_sys (
  .refclk(CLK_50M),
  .rst(rst_pll),
  .locked(pll_sys_locked),
  .outclk_0(clk_sys),
  .outclk_1(clk_sdram)
);

pll_video pll_video (
  .refclk(CLK_50M),
  .rst(rst_pll),
  .locked(pll_video_locked),
  .outclk_0(clk_video),
);

assign DDRAM_CLK = clk_sys;
assign SDRAM_CLK = clk_sdram;
assign CLK_VIDEO = clk_video;

reset_ctrl reset_sys_ctrl (
  .clk(clk_sys),
  .rst_i(RESET | ~pll_sys_locked),
  .rst_o(rst_sys)
);

reset_ctrl reset_cpu_ctrl (
  .clk(clk_sys),
  .rst_i(RESET | ~pll_sys_locked | status[0] | buttons[1]),
  .rst_o(rst_cpu)
);

reset_ctrl reset_video_ctrl (
  .clk(clk_video),
  .rst_i(RESET | ~pll_video_locked),
  .rst_o(rst_video)
);

////////////////////////////////////////////////////////////////////////////////
// HPS IO
////////////////////////////////////////////////////////////////////////////////

wire  [1:0] buttons;
wire [31:0] status;
wire        forced_scandoubler;
wire [21:0] gamma_bus;
wire        new_vmode;
wire        direct_video;
wire [15:0] sdram_sz;

wire        ioctl_upload;
wire        ioctl_download;
wire        ioctl_rd;
wire        ioctl_wr;
wire        ioctl_wait;
wire  [7:0] ioctl_index;
wire [26:0] ioctl_addr;
wire [15:0] ioctl_din;
wire [15:0] ioctl_dout;

wire [10:0] ps2_key;
wire [31:0] joystick_0, joystick_1;

wire sdram_available = |sdram_sz[1:0];

hps_io #(.CONF_STR(CONF_STR), .WIDE(1)) hps_io (
  .clk_sys(clk_sys),
  .HPS_BUS(HPS_BUS),

  .buttons(buttons),
  .status(status),
  .status_menumask({sdram_available, direct_video}),
  .forced_scandoubler(forced_scandoubler),
  .new_vmode(new_vmode),
  .gamma_bus(gamma_bus),
  .direct_video(direct_video),
  .sdram_sz(sdram_sz),

  .ioctl_upload(ioctl_upload),
  .ioctl_download(ioctl_download),
  .ioctl_rd(ioctl_rd),
  .ioctl_wr(ioctl_wr),
  .ioctl_wait(ioctl_wait),
  .ioctl_index(ioctl_index),
  .ioctl_addr(ioctl_addr),
  .ioctl_din(ioctl_din),
  .ioctl_dout(ioctl_dout),

  .joystick_0(joystick_0),
  .joystick_1(joystick_1),

  .ps2_key(ps2_key)
);

////////////////////////////////////////////////////////////////////////////////
// VIDEO
////////////////////////////////////////////////////////////////////////////////

wire ce_pix;
wire [7:0] r, g, b;
wire hsync, vsync;
wire hblank, vblank;
wire [2:0] fx = status[6:4];
wire [2:0] sl = fx ? fx - 1'd1 : 3'd0;
wire scandoubler = fx || forced_scandoubler;

assign VGA_F1 = 0;
assign VGA_SL = sl[1:0];
assign VGA_SCALER = 0;
assign HDMI_FREEZE = 0;

video_mixer #(.LINE_LENGTH(320), .HALF_DEPTH(0), .GAMMA(1)) video_mixer (
  .CLK_VIDEO(clk_video),
  .CE_PIXEL(CE_PIXEL),
  .ce_pix(ce_pix),

  .scandoubler(scandoubler),
  .hq2x(fx==1),
  .gamma_bus(gamma_bus),

  .R(r),
  .G(g),
  .B(b),

  .HSync(hsync),
  .VSync(vsync),
  .HBlank(hblank),
  .VBlank(vblank),

  .VGA_R(VGA_R),
  .VGA_G(VGA_G),
  .VGA_B(VGA_B),
  .VGA_VS(VGA_VS),
  .VGA_HS(VGA_HS),
  .VGA_DE(VGA_DE)
);

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
wire player_2_up       = key_r     | joystick_1[3];
wire player_2_down     = key_f     | joystick_1[2];
wire player_2_left     = key_d     | joystick_1[1];
wire player_2_right    = key_g     | joystick_1[0];
wire player_2_button_1 = key_a     | joystick_1[4];
wire player_2_button_2 = key_s     | joystick_1[5];
wire player_2_button_3 = key_q     | joystick_1[6];
wire player_2_start    = key_2     | joystick_1[7];
wire player_2_coin     = key_6     | joystick_1[8];
wire player_2_pause    =             joystick_1[9];
wire service_1         = key_9     | joystick_0[10];
wire service_2         = key_0     | joystick_1[10];

////////////////////////////////////////////////////////////////////////////////
// CAVE
////////////////////////////////////////////////////////////////////////////////

wire [31:0] ddr_addr;
wire        sdram_oe;
wire [15:0] sdram_din;
wire [15:0] sdram_dout;

assign DDRAM_ADDR = ddr_addr[31:3];
assign SDRAM_DQ = sdram_oe ? sdram_din : 16'bZ;
assign sdram_dout = SDRAM_DQ;

Main main (
  // System clock/reset
  .clock(clk_sys),
  .reset(rst_sys),
  // CPU reset
  .cpuReset(rst_cpu),
  // Video clock/reset
  .videoClock(clk_video),
  .videoReset(rst_video),
  // Options
  .options_sdram(sdram_available),
  .options_offset_x(status[27:24]),
  .options_offset_y(status[31:28]),
  .options_rotate(status[2]),
  .options_flip(status[3]),
  .options_compatibility(status[7]),
  .options_layerEnable_sprite(~status[10]),
  .options_layerEnable_layer_0(~status[11]),
  .options_layerEnable_layer_1(~status[12]),
  .options_layerEnable_layer_2(~status[13]),
  .options_rowScrollEnable(~status[14]),
  .options_rowSelectEnable(~status[15]),
  .options_gameIndex(status[19:16]),
  // Joystick signals
  .joystick_player1_up(player_1_up),
  .joystick_player1_down(player_1_down),
  .joystick_player1_left(player_1_left),
  .joystick_player1_right(player_1_right),
  .joystick_player1_buttons({player_1_button_3, player_1_button_2, player_1_button_1}),
  .joystick_player1_start(player_1_start),
  .joystick_player1_coin(player_1_coin),
  .joystick_player1_pause(player_1_pause),
  .joystick_player2_up(player_2_up),
  .joystick_player2_down(player_2_down),
  .joystick_player2_left(player_2_left),
  .joystick_player2_right(player_2_right),
  .joystick_player2_buttons({player_2_button_3, player_2_button_2, player_2_button_1}),
  .joystick_player2_start(player_2_start),
  .joystick_player2_coin(player_2_coin),
  .joystick_player2_pause(player_2_pause),
  .joystick_service1(service_1),
  .joystick_service2(service_2),
  // Video signals
  .video_clockEnable(ce_pix),
  .video_hSync(hsync),
  .video_vSync(vsync),
  .video_hBlank(hblank),
  .video_vBlank(vblank),
  .changeVideoMode(new_vmode),
  // Frame buffer control signals
  .frameBufferCtrl_enable(FB_EN),
  .frameBufferCtrl_hSize(FB_WIDTH),
  .frameBufferCtrl_vSize(FB_HEIGHT),
  .frameBufferCtrl_format(FB_FORMAT),
  .frameBufferCtrl_baseAddr(FB_BASE),
  .frameBufferCtrl_stride(FB_STRIDE),
  .frameBufferCtrl_vBlank(FB_VBL),
  .frameBufferCtrl_lowLat(FB_LL),
  .frameBufferCtrl_forceBlank(FB_FORCE_BLANK),
  // DDR
  .ddr_rd(DDRAM_RD),
  .ddr_wr(DDRAM_WE),
  .ddr_addr(ddr_addr),
  .ddr_mask(DDRAM_BE),
  .ddr_din(DDRAM_DIN),
  .ddr_dout(DDRAM_DOUT),
  .ddr_waitReq(DDRAM_BUSY),
  .ddr_valid(DDRAM_DOUT_READY),
  .ddr_burstLength(DDRAM_BURSTCNT),
  // SDRAM
  .sdram_cke(SDRAM_CKE),
  .sdram_cs_n(SDRAM_nCS),
  .sdram_ras_n(SDRAM_nRAS),
  .sdram_cas_n(SDRAM_nCAS),
  .sdram_we_n(SDRAM_nWE),
  .sdram_oe(sdram_oe),
  .sdram_bank(SDRAM_BA),
  .sdram_addr(SDRAM_A),
  .sdram_din(sdram_din),
  .sdram_dout(sdram_dout),
  // Download
  .ioctl_upload(ioctl_upload),
  .ioctl_download(ioctl_download),
  .ioctl_rd(ioctl_rd),
  .ioctl_wr(ioctl_wr),
  .ioctl_waitReq(ioctl_wait),
  .ioctl_index(ioctl_index),
  .ioctl_addr(ioctl_addr),
  .ioctl_din(ioctl_din),
  .ioctl_dout(ioctl_dout),
  // RGB output
  .rgb_r(r),
  .rgb_g(g),
  .rgb_b(b),
  // Audio output
  .audio_left(AUDIO_L),
  .audio_right(AUDIO_R),
  // LEDs
  .led_power(LED_POWER[0]),
  .led_disk(LED_DISK[0]),
  .led_user(LED_USER)
);

endmodule
