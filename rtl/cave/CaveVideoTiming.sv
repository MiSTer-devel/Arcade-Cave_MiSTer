module CaveVideoTiming #(
  parameter [9:0] H_TOTAL = 10'h1C0,
  parameter [9:0] V_TOTAL = 10'h110
) (
  input        clock,
  input        reset,
  input  [8:0] io_display_x,
  input  [8:0] io_display_y,
  input  [8:0] io_frontPorch_x,
  input  [8:0] io_frontPorch_y,
  input  [8:0] io_retrace_x,
  input  [8:0] io_retrace_y,
  input  [3:0] io_offset_x,
  input  [3:0] io_offset_y,
  output       io_timing_clockEnable,
  output       io_timing_displayEnable,
  output [8:0] io_timing_pos_x,
  output [8:0] io_timing_pos_y,
  output       io_timing_hSync,
  output       io_timing_vSync,
  output       io_timing_hBlank,
  output       io_timing_vBlank
);
  reg [1:0] clock_divider;
  reg [8:0] x;
  reg [8:0] y;

  wire [9:0] x_ext = {1'b0, x};
  wire [9:0] y_ext = {1'b0, y};
  wire [9:0] offset_x_ext = {{6{io_offset_x[3]}}, io_offset_x};
  wire [9:0] offset_y_ext = {{6{io_offset_y[3]}}, io_offset_y};
  wire [9:0] display_x_ext = {1'b0, io_display_x};
  wire [9:0] display_y_ext = {1'b0, io_display_y};
  wire [9:0] front_porch_x_ext = {1'b0, io_frontPorch_x};
  wire [9:0] front_porch_y_ext = {1'b0, io_frontPorch_y};
  wire [9:0] retrace_x_ext = {1'b0, io_retrace_x};
  wire [9:0] retrace_y_ext = {1'b0, io_retrace_y};

  wire [9:0] h_end_display_offset = offset_x_ext + H_TOTAL;
  wire [9:0] h_begin_display =
    h_end_display_offset - display_x_ext - front_porch_x_ext - retrace_x_ext;
  wire [9:0] h_end_display =
    h_end_display_offset - front_porch_x_ext - retrace_x_ext;
  wire [8:0] h_begin_sync = H_TOTAL[8:0] - io_retrace_x;

  wire [9:0] v_end_display_offset = offset_y_ext + V_TOTAL;
  wire [9:0] v_begin_display =
    v_end_display_offset - display_y_ext - front_porch_y_ext - retrace_y_ext;
  wire [9:0] v_end_display =
    v_end_display_offset - front_porch_y_ext - retrace_y_ext;
  wire [8:0] v_begin_sync = V_TOTAL[8:0] - io_retrace_y;

  wire h_blank = (x_ext < h_begin_display) | (x_ext >= h_end_display);
  wire v_blank = (y_ext < v_begin_display) | (y_ext >= v_end_display);
  wire h_wrap = x == (H_TOTAL[8:0] - 9'd1);
  wire v_wrap = y == (V_TOTAL[8:0] - 9'd1);

  always @(posedge clock) begin
    if (reset) begin
      clock_divider <= 2'd0;
      x <= 9'd0;
      y <= 9'd0;
    end
    else begin
      clock_divider <= clock_divider + 2'd1;

      if (&clock_divider)
        x <= h_wrap ? 9'd0 : x + 9'd1;

      if ((&clock_divider) & h_wrap)
        y <= v_wrap ? 9'd0 : y + 9'd1;
    end
  end

  assign io_timing_clockEnable = &clock_divider;
  assign io_timing_displayEnable = ~(h_blank | v_blank);
  assign io_timing_pos_x = x - h_begin_display[8:0];
  assign io_timing_pos_y = y - v_begin_display[8:0];
  assign io_timing_hSync = (x >= h_begin_sync) & (x < H_TOTAL[8:0]);
  assign io_timing_vSync = (y >= v_begin_sync) & (y < V_TOTAL[8:0]);
  assign io_timing_hBlank = h_blank;
  assign io_timing_vBlank = v_blank;
endmodule
