module BurstMemArbiter_1(
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

  reg         busyReg;
  reg  [7:0]  indexReg;
  wire [7:0]  _index_enc_T_1 = io_in_6_rd ? 8'h40 : {io_in_7_rd, 7'h0};
  wire [7:0]  _index_enc_T_2 = io_in_5_rd ? 8'h20 : _index_enc_T_1;
  wire [7:0]  _index_enc_T_3 = io_in_4_rd ? 8'h10 : _index_enc_T_2;
  wire [7:0]  _index_enc_T_4 = io_in_3_rd ? 8'h8 : _index_enc_T_3;
  wire [7:0]  _index_enc_T_5 = io_in_2_rd | io_in_2_wr ? 8'h4 : _index_enc_T_4;
  wire [7:0]  _index_enc_T_6 = io_in_1_rd ? 8'h2 : _index_enc_T_5;
  wire [7:0]  index_enc = io_in_0_wr ? 8'h1 : _index_enc_T_6;
  wire [7:0]  chosen = busyReg ? indexReg : index_enc;
  wire        io_out_rd_0 =
    chosen[1] & io_in_1_rd | chosen[2] & io_in_2_rd | chosen[3] & io_in_3_rd | chosen[4]
    & io_in_4_rd | chosen[5] & io_in_5_rd | chosen[6] & io_in_6_rd | chosen[7]
    & io_in_7_rd;
  wire        io_out_wr_0 = chosen[0] & io_in_0_wr | chosen[2] & io_in_2_wr;
  wire [24:0] _io_out_mem_addr_T = chosen[0] ? io_in_0_addr : 25'h0;
  wire [24:0] _io_out_mem_addr_T_1 = chosen[1] ? io_in_1_addr : 25'h0;
  wire [24:0] _io_out_mem_addr_T_2 = chosen[2] ? io_in_2_addr : 25'h0;
  wire [24:0] _io_out_mem_addr_T_3 = chosen[3] ? io_in_3_addr : 25'h0;
  wire [24:0] _io_out_mem_addr_T_4 = chosen[4] ? io_in_4_addr : 25'h0;
  wire [24:0] _io_out_mem_addr_T_5 = chosen[5] ? io_in_5_addr : 25'h0;
  wire [24:0] _io_out_mem_addr_T_6 = chosen[6] ? io_in_6_addr : 25'h0;
  wire [24:0] _io_out_mem_addr_T_7 = chosen[7] ? io_in_7_addr : 25'h0;
  wire [15:0] _io_out_mem_din_T = chosen[0] ? io_in_0_din : 16'h0;
  wire [15:0] _io_out_mem_din_T_2 = chosen[2] ? io_in_2_din : 16'h0;
  wire        _io_out_io_in_7_wait_n_T = chosen == 8'h0;
  wire        effectiveRequest = ~busyReg & (io_out_rd_0 | io_out_wr_0) & io_out_wait_n;
  always @(posedge clock) begin
    if (reset) begin
      busyReg <= 1'h0;
      indexReg <= 8'h0;
    end
    else begin
      busyReg <= ~io_out_burstDone & (effectiveRequest | busyReg);
      if (io_out_burstDone | ~effectiveRequest) begin
      end
      else
        indexReg <= index_enc;
    end
  end // always @(posedge)
  assign io_in_0_wait_n = (_io_out_io_in_7_wait_n_T | chosen[0]) & io_out_wait_n;
  assign io_in_0_burstDone = chosen[0] & io_out_burstDone;
  assign io_in_1_dout = io_out_dout;
  assign io_in_1_wait_n = (_io_out_io_in_7_wait_n_T | chosen[1]) & io_out_wait_n;
  assign io_in_1_valid = chosen[1] & io_out_valid;
  assign io_in_2_dout = io_out_dout;
  assign io_in_2_wait_n = (_io_out_io_in_7_wait_n_T | chosen[2]) & io_out_wait_n;
  assign io_in_2_valid = chosen[2] & io_out_valid;
  assign io_in_3_dout = io_out_dout;
  assign io_in_3_wait_n = (_io_out_io_in_7_wait_n_T | chosen[3]) & io_out_wait_n;
  assign io_in_3_valid = chosen[3] & io_out_valid;
  assign io_in_4_dout = io_out_dout;
  assign io_in_4_wait_n = (_io_out_io_in_7_wait_n_T | chosen[4]) & io_out_wait_n;
  assign io_in_4_valid = chosen[4] & io_out_valid;
  assign io_in_5_dout = io_out_dout;
  assign io_in_5_wait_n = (_io_out_io_in_7_wait_n_T | chosen[5]) & io_out_wait_n;
  assign io_in_5_valid = chosen[5] & io_out_valid;
  assign io_in_6_dout = io_out_dout;
  assign io_in_6_wait_n = (_io_out_io_in_7_wait_n_T | chosen[6]) & io_out_wait_n;
  assign io_in_6_valid = chosen[6] & io_out_valid;
  assign io_in_7_dout = io_out_dout;
  assign io_in_7_wait_n = (_io_out_io_in_7_wait_n_T | chosen[7]) & io_out_wait_n;
  assign io_in_7_valid = chosen[7] & io_out_valid;
  assign io_out_rd = io_out_rd_0;
  assign io_out_wr = io_out_wr_0;
  assign io_out_addr =
    _io_out_mem_addr_T | _io_out_mem_addr_T_1 | _io_out_mem_addr_T_2
    | _io_out_mem_addr_T_3 | _io_out_mem_addr_T_4 | _io_out_mem_addr_T_5
    | _io_out_mem_addr_T_6 | _io_out_mem_addr_T_7;
  assign io_out_din = _io_out_mem_din_T | _io_out_mem_din_T_2;
endmodule

