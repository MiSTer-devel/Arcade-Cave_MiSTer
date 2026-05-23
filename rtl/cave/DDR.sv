module DDR(
  input         clock,
  input         reset,
  input         io_mem_rd,
  input         io_mem_wr,
  input  [31:0] io_mem_addr,
  input  [7:0]  io_mem_mask,
  input  [63:0] io_mem_din,
  output [63:0] io_mem_dout,
  output        io_mem_wait_n,
  output        io_mem_valid,
  input  [7:0]  io_mem_burstLength,
  output        io_mem_burstDone,
  output        io_ddr_rd,
  output        io_ddr_wr,
  output [31:0] io_ddr_addr,
  output [7:0]  io_ddr_mask,
  output [63:0] io_ddr_din,
  input  [63:0] io_ddr_dout,
  input         io_ddr_wait_n,
  input         io_ddr_valid,
  output [7:0]  io_ddr_burstLength
);
  localparam [1:0] STATE_IDLE       = 2'd0;
  localparam [1:0] STATE_READ_WAIT  = 2'd1;
  localparam [1:0] STATE_WRITE_WAIT = 2'd2;

  reg [1:0] stateReg;
  reg [7:0] burstLengthReg;
  reg [7:0] burstCounter;

  wire idle = stateReg == STATE_IDLE;
  wire read = idle && io_mem_rd;
  wire write = (idle || stateReg == STATE_WRITE_WAIT) && io_mem_wr;
  wire acceptedRead = read && io_ddr_wait_n;
  wire acceptedWrite = write && io_ddr_wait_n;

  wire [7:0] burstLength = idle ? io_mem_burstLength : burstLengthReg;
  wire burstCounterLast = burstCounter == 8'(burstLength - 8'h1) || burstLength == 8'h0;
  wire burstCounterEnable = (stateReg == STATE_READ_WAIT && io_ddr_valid) || acceptedWrite;
  wire burstCounterWrap = burstCounterEnable && burstCounterLast;

  always @(posedge clock) begin
    if (reset) begin
      stateReg <= STATE_IDLE;
      burstCounter <= 8'h0;
    end
    else begin
      if (burstCounterWrap)
        stateReg <= STATE_IDLE;
      else if (acceptedRead)
        stateReg <= STATE_READ_WAIT;
      else if (acceptedWrite)
        stateReg <= STATE_WRITE_WAIT;

      if (burstCounterEnable)
        burstCounter <= burstCounterLast ? 8'h0 : 8'(burstCounter + 8'h1);
    end

    if (idle)
      burstLengthReg <= io_mem_burstLength;
  end

  assign io_mem_dout = io_ddr_dout;
  assign io_mem_wait_n = io_ddr_wait_n;
  assign io_mem_valid = io_ddr_valid;
  assign io_mem_burstDone = burstCounterWrap;

  assign io_ddr_rd = read;
  assign io_ddr_wr = write;
  assign io_ddr_addr = io_mem_addr;
  assign io_ddr_mask = io_mem_mask;
  assign io_ddr_din = io_mem_din;
  assign io_ddr_burstLength = burstLength;
endmodule
