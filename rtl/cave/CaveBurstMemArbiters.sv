// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module CaveMainDdrArbiter(
  input         clock,
  input         reset,
  input         io_in_0_wr,
  input  [31:0] io_in_0_addr,
  input  [63:0] io_in_0_din,
  output        io_in_0_burstDone,
  input         io_in_1_rd,
  input  [31:0] io_in_1_addr,
  output [63:0] io_in_1_dout,
  output        io_in_1_wait_n,
  output        io_in_1_valid,
  output        io_in_1_burstDone,
  input         io_in_2_wr,
  input  [31:0] io_in_2_addr,
  input  [7:0]  io_in_2_mask,
  input  [63:0] io_in_2_din,
  output        io_in_2_wait_n,
  input         io_in_3_rd,
  input         io_in_3_wr,
  input  [31:0] io_in_3_addr,
  input  [7:0]  io_in_3_mask,
  input  [63:0] io_in_3_din,
  output [63:0] io_in_3_dout,
  output        io_in_3_wait_n,
  output        io_in_3_valid,
  input  [7:0]  io_in_3_burstLength,
  output        io_in_3_burstDone,
  input         io_in_4_rd,
  input  [31:0] io_in_4_addr,
  output [63:0] io_in_4_dout,
  output        io_in_4_wait_n,
  output        io_in_4_valid,
  input  [7:0]  io_in_4_burstLength,
  output        io_in_4_burstDone,
  input         io_in_5_rd,
  input         io_in_5_wr,
  input  [31:0] io_in_5_addr,
  input  [7:0]  io_in_5_mask,
  input  [63:0] io_in_5_din,
  output [63:0] io_in_5_dout,
  output        io_in_5_wait_n,
  output        io_in_5_valid,
  input  [7:0]  io_in_5_burstLength,
  output        io_in_5_burstDone,
  output        io_out_rd,
  output        io_out_wr,
  output [31:0] io_out_addr,
  output [7:0]  io_out_mask,
  output [63:0] io_out_din,
  input  [63:0] io_out_dout,
  input         io_out_wait_n,
  input         io_out_valid,
  output [7:0]  io_out_burstLength,
  input         io_out_burstDone
);

  localparam [5:0] REQ_NONE = 6'b000000;
  localparam [5:0] REQ_0    = 6'b000001;
  localparam [5:0] REQ_1    = 6'b000010;
  localparam [5:0] REQ_2    = 6'b000100;
  localparam [5:0] REQ_3    = 6'b001000;
  localparam [5:0] REQ_4    = 6'b010000;
  localparam [5:0] REQ_5    = 6'b100000;

  reg        busy_reg;
  reg [5:0]  locked_request;

  wire request_0 = io_in_0_wr;
  wire request_1 = io_in_1_rd;
  wire request_2 = io_in_2_wr;
  wire request_3 = io_in_3_rd | io_in_3_wr;
  wire request_4 = io_in_4_rd;
  wire request_5 = io_in_5_rd | io_in_5_wr;

  wire [5:0] next_request =
    request_0 ? REQ_0 :
    request_1 ? REQ_1 :
    request_5 ? REQ_5 :
    request_2 ? REQ_2 :
    request_3 ? REQ_3 :
    request_4 ? REQ_4 :
    REQ_NONE;

  wire [5:0] chosen = busy_reg ? locked_request : next_request;
  wire       no_request_chosen = chosen == REQ_NONE;
  wire       selected_read =
    (chosen[1] & io_in_1_rd) | (chosen[3] & io_in_3_rd) | (chosen[4] & io_in_4_rd)
    | (chosen[5] & io_in_5_rd);
  wire       selected_write =
    (chosen[0] & io_in_0_wr) | (chosen[2] & io_in_2_wr) | (chosen[3] & io_in_3_wr)
    | (chosen[5] & io_in_5_wr);
  wire       effective_request = ~busy_reg & (selected_read | selected_write) & io_out_wait_n;

  always @(posedge clock) begin
    if (reset) begin
      busy_reg <= 1'b0;
      locked_request <= REQ_NONE;
    end
    else begin
      busy_reg <= ~io_out_burstDone & (effective_request | busy_reg);
      if (effective_request & ~io_out_burstDone)
        locked_request <= next_request;
    end
  end // always @(posedge)

  assign io_in_0_burstDone = chosen[0] & io_out_burstDone;
  assign io_in_1_dout = io_out_dout;
  assign io_in_1_wait_n = (no_request_chosen | chosen[1]) & io_out_wait_n;
  assign io_in_1_valid = chosen[1] & io_out_valid;
  assign io_in_1_burstDone = chosen[1] & io_out_burstDone;
  assign io_in_2_wait_n = (no_request_chosen | chosen[2]) & io_out_wait_n;
  assign io_in_3_dout = io_out_dout;
  assign io_in_3_wait_n = (no_request_chosen | chosen[3]) & io_out_wait_n;
  assign io_in_3_valid = chosen[3] & io_out_valid;
  assign io_in_3_burstDone = chosen[3] & io_out_burstDone;
  assign io_in_4_dout = io_out_dout;
  assign io_in_4_wait_n = (no_request_chosen | chosen[4]) & io_out_wait_n;
  assign io_in_4_valid = chosen[4] & io_out_valid;
  assign io_in_4_burstDone = chosen[4] & io_out_burstDone;
  assign io_in_5_dout = io_out_dout;
  assign io_in_5_wait_n = (no_request_chosen | chosen[5]) & io_out_wait_n;
  assign io_in_5_valid = chosen[5] & io_out_valid;
  assign io_in_5_burstDone = chosen[5] & io_out_burstDone;
  assign io_out_rd = selected_read;
  assign io_out_wr = selected_write;
  assign io_out_addr =
    (chosen[0] ? io_in_0_addr : 32'h0) |
    (chosen[1] ? io_in_1_addr : 32'h0) |
    (chosen[2] ? io_in_2_addr : 32'h0) |
    (chosen[3] ? io_in_3_addr : 32'h0) |
    (chosen[4] ? io_in_4_addr : 32'h0) |
    (chosen[5] ? io_in_5_addr : 32'h0);
  assign io_out_mask =
    {8{chosen[0]}} |
    (chosen[2] ? io_in_2_mask : 8'h0) |
    (chosen[3] ? io_in_3_mask : 8'h0) |
    (chosen[5] ? io_in_5_mask : 8'h0);
  assign io_out_din =
    (chosen[0] ? io_in_0_din : 64'h0) |
    (chosen[2] ? io_in_2_din : 64'h0) |
    (chosen[3] ? io_in_3_din : 64'h0) |
    (chosen[5] ? io_in_5_din : 64'h0);
  assign io_out_burstLength =
    (chosen[0] ? 8'd1 : 8'd0) |
    (chosen[1] ? 8'd16 : 8'd0) |
    (chosen[2] ? 8'd1 : 8'd0) |
    (chosen[3] ? io_in_3_burstLength : 8'd0) |
    (chosen[4] ? io_in_4_burstLength : 8'd0) |
    (chosen[5] ? io_in_5_burstLength : 8'd0);
endmodule

module CaveMainSdramArbiter(
  input         clock,
  input         reset,
  input         io_in_0_wr,
  input  [24:0] io_in_0_addr,
  input  [15:0] io_in_0_din,
  output        io_in_0_wait_n,
  output        io_in_0_burstDone,
  input         io_in_1_rd,
  input  [24:0] io_in_1_addr,
  output [15:0] io_in_1_dout,
  output        io_in_1_wait_n,
  output        io_in_1_valid,
  input         io_in_2_rd,
  input         io_in_2_wr,
  input  [24:0] io_in_2_addr,
  input  [15:0] io_in_2_din,
  output [15:0] io_in_2_dout,
  output        io_in_2_wait_n,
  output        io_in_2_valid,
  input         io_in_3_rd,
  input  [24:0] io_in_3_addr,
  output [15:0] io_in_3_dout,
  output        io_in_3_wait_n,
  output        io_in_3_valid,
  input         io_in_4_rd,
  input  [24:0] io_in_4_addr,
  output [15:0] io_in_4_dout,
  output        io_in_4_wait_n,
  output        io_in_4_valid,
  input         io_in_5_rd,
  input  [24:0] io_in_5_addr,
  output [15:0] io_in_5_dout,
  output        io_in_5_wait_n,
  output        io_in_5_valid,
  input         io_in_6_rd,
  input  [24:0] io_in_6_addr,
  output [15:0] io_in_6_dout,
  output        io_in_6_wait_n,
  output        io_in_6_valid,
  input         io_in_7_rd,
  input  [24:0] io_in_7_addr,
  output [15:0] io_in_7_dout,
  output        io_in_7_wait_n,
  output        io_in_7_valid,
  output        io_out_rd,
  output        io_out_wr,
  output [24:0] io_out_addr,
  output [15:0] io_out_din,
  input  [15:0] io_out_dout,
  input         io_out_wait_n,
  input         io_out_valid,
  input         io_out_burstDone
);

  localparam [7:0] REQ_NONE = 8'b00000000;
  localparam [7:0] REQ_0    = 8'b00000001;
  localparam [7:0] REQ_1    = 8'b00000010;
  localparam [7:0] REQ_2    = 8'b00000100;
  localparam [7:0] REQ_3    = 8'b00001000;
  localparam [7:0] REQ_4    = 8'b00010000;
  localparam [7:0] REQ_5    = 8'b00100000;
  localparam [7:0] REQ_6    = 8'b01000000;
  localparam [7:0] REQ_7    = 8'b10000000;

  reg        busy_reg;
  reg [7:0]  locked_request;

  wire request_0 = io_in_0_wr;
  wire request_1 = io_in_1_rd;
  wire request_2 = io_in_2_rd | io_in_2_wr;
  wire request_3 = io_in_3_rd;
  wire request_4 = io_in_4_rd;
  wire request_5 = io_in_5_rd;
  wire request_6 = io_in_6_rd;
  wire request_7 = io_in_7_rd;

  wire [7:0] next_request =
    request_0 ? REQ_0 :
    request_1 ? REQ_1 :
    request_2 ? REQ_2 :
    request_3 ? REQ_3 :
    request_4 ? REQ_4 :
    request_5 ? REQ_5 :
    request_6 ? REQ_6 :
    request_7 ? REQ_7 :
    REQ_NONE;

  wire [7:0] chosen = busy_reg ? locked_request : next_request;
  wire       no_request_chosen = chosen == REQ_NONE;
  wire       selected_read =
    (chosen[1] & io_in_1_rd) |
    (chosen[2] & io_in_2_rd) |
    (chosen[3] & io_in_3_rd) |
    (chosen[4] & io_in_4_rd) |
    (chosen[5] & io_in_5_rd) |
    (chosen[6] & io_in_6_rd) |
    (chosen[7] & io_in_7_rd);
  wire       selected_write = (chosen[0] & io_in_0_wr) | (chosen[2] & io_in_2_wr);
  wire       effective_request = ~busy_reg & (selected_read | selected_write) & io_out_wait_n;

  always @(posedge clock) begin
    if (reset) begin
      busy_reg <= 1'b0;
      locked_request <= REQ_NONE;
    end
    else begin
      busy_reg <= ~io_out_burstDone & (effective_request | busy_reg);
      if (effective_request & ~io_out_burstDone)
        locked_request <= next_request;
    end
  end // always @(posedge)

  assign io_in_0_wait_n = (no_request_chosen | chosen[0]) & io_out_wait_n;
  assign io_in_0_burstDone = chosen[0] & io_out_burstDone;
  assign io_in_1_dout = io_out_dout;
  assign io_in_1_wait_n = (no_request_chosen | chosen[1]) & io_out_wait_n;
  assign io_in_1_valid = chosen[1] & io_out_valid;
  assign io_in_2_dout = io_out_dout;
  assign io_in_2_wait_n = (no_request_chosen | chosen[2]) & io_out_wait_n;
  assign io_in_2_valid = chosen[2] & io_out_valid;
  assign io_in_3_dout = io_out_dout;
  assign io_in_3_wait_n = (no_request_chosen | chosen[3]) & io_out_wait_n;
  assign io_in_3_valid = chosen[3] & io_out_valid;
  assign io_in_4_dout = io_out_dout;
  assign io_in_4_wait_n = (no_request_chosen | chosen[4]) & io_out_wait_n;
  assign io_in_4_valid = chosen[4] & io_out_valid;
  assign io_in_5_dout = io_out_dout;
  assign io_in_5_wait_n = (no_request_chosen | chosen[5]) & io_out_wait_n;
  assign io_in_5_valid = chosen[5] & io_out_valid;
  assign io_in_6_dout = io_out_dout;
  assign io_in_6_wait_n = (no_request_chosen | chosen[6]) & io_out_wait_n;
  assign io_in_6_valid = chosen[6] & io_out_valid;
  assign io_in_7_dout = io_out_dout;
  assign io_in_7_wait_n = (no_request_chosen | chosen[7]) & io_out_wait_n;
  assign io_in_7_valid = chosen[7] & io_out_valid;
  assign io_out_rd = selected_read;
  assign io_out_wr = selected_write;
  assign io_out_addr =
    (chosen[0] ? io_in_0_addr : 25'h0) |
    (chosen[1] ? io_in_1_addr : 25'h0) |
    (chosen[2] ? io_in_2_addr : 25'h0) |
    (chosen[3] ? io_in_3_addr : 25'h0) |
    (chosen[4] ? io_in_4_addr : 25'h0) |
    (chosen[5] ? io_in_5_addr : 25'h0) |
    (chosen[6] ? io_in_6_addr : 25'h0) |
    (chosen[7] ? io_in_7_addr : 25'h0);
  assign io_out_din =
    (chosen[0] ? io_in_0_din : 16'h0) |
    (chosen[2] ? io_in_2_din : 16'h0);
endmodule

module CaveSpriteFramebufferDdrArbiter(
  input         clock,
  input         reset,
  input         io_in_0_rd,
  input  [31:0] io_in_0_addr,
  output [63:0] io_in_0_dout,
  output        io_in_0_wait_n,
  output        io_in_0_valid,
  output        io_in_0_burstDone,
  input         io_in_1_wr,
  input  [31:0] io_in_1_addr,
  input  [63:0] io_in_1_din,
  input  [7:0]  io_in_1_burstLength,
  output        io_in_1_wait_n,
  output        io_in_1_burstDone,
  input         io_in_2_wr,
  input  [31:0] io_in_2_addr,
  input  [7:0]  io_in_2_mask,
  input  [63:0] io_in_2_din,
  output        io_in_2_wait_n,
  output        io_out_rd,
  output        io_out_wr,
  output [31:0] io_out_addr,
  output [7:0]  io_out_mask,
  output [63:0] io_out_din,
  input  [63:0] io_out_dout,
  input         io_out_wait_n,
  input         io_out_valid,
  output [7:0]  io_out_burstLength,
  input         io_out_burstDone
);

  localparam [2:0] REQ_NONE = 3'b000;
  localparam [2:0] REQ_0    = 3'b001;
  localparam [2:0] REQ_1    = 3'b010;
  localparam [2:0] REQ_2    = 3'b100;

  reg        busy_reg;
  reg [2:0]  locked_request;

  wire request_0 = io_in_0_rd;
  wire request_1 = io_in_1_wr;
  wire request_2 = io_in_2_wr;

  wire [2:0] next_request =
    request_0 ? REQ_0 :
    request_1 ? REQ_1 :
    request_2 ? REQ_2 :
    REQ_NONE;

  wire [2:0] chosen = busy_reg ? locked_request : next_request;
  wire       no_request_chosen = chosen == REQ_NONE;
  wire       selected_read = chosen[0] & io_in_0_rd;
  wire       selected_write = (chosen[1] & io_in_1_wr) | (chosen[2] & io_in_2_wr);
  wire       effective_request = ~busy_reg & (selected_read | selected_write) & io_out_wait_n;

  always @(posedge clock) begin
    if (reset) begin
      busy_reg <= 1'b0;
      locked_request <= REQ_NONE;
    end
    else begin
      busy_reg <= ~io_out_burstDone & (effective_request | busy_reg);
      if (effective_request & ~io_out_burstDone)
        locked_request <= next_request;
    end
  end // always @(posedge)

  assign io_in_0_dout = io_out_dout;
  assign io_in_0_wait_n = (no_request_chosen | chosen[0]) & io_out_wait_n;
  assign io_in_0_valid = chosen[0] & io_out_valid;
  assign io_in_0_burstDone = chosen[0] & io_out_burstDone;
  assign io_in_1_wait_n = (no_request_chosen | chosen[1]) & io_out_wait_n;
  assign io_in_1_burstDone = chosen[1] & io_out_burstDone;
  assign io_in_2_wait_n = (no_request_chosen | chosen[2]) & io_out_wait_n;
  assign io_out_rd = selected_read;
  assign io_out_wr = selected_write;
  assign io_out_addr =
    (chosen[0] ? io_in_0_addr : 32'h0) |
    (chosen[1] ? io_in_1_addr : 32'h0) |
    (chosen[2] ? io_in_2_addr : 32'h0);
  assign io_out_mask = {8{chosen[1]}} | (chosen[2] ? io_in_2_mask : 8'h0);
  assign io_out_din =
    (chosen[1] ? io_in_1_din : 64'h0) |
    (chosen[2] ? io_in_2_din : 64'h0);
  assign io_out_burstLength =
    (chosen[0] ? 8'd16 : 8'd0) |
    (chosen[1] ? io_in_1_burstLength : 8'd0) |
    (chosen[2] ? 8'd1 : 8'd0);
endmodule
