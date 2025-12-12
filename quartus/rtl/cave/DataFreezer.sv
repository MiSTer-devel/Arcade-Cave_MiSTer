
// Users can define 'PRINTF_COND' to add an extra gate to prints.
`ifndef PRINTF_COND_
  `ifdef PRINTF_COND
    `define PRINTF_COND_ (`PRINTF_COND)
  `else  // PRINTF_COND
    `define PRINTF_COND_ 1
  `endif // PRINTF_COND
`endif // not def PRINTF_COND_
module DataFreezer(
  input         clock,
  input         reset,
  input         io_targetClock,
  input         io_in_rd,
  input         io_in_wr,
  input  [6:0]  io_in_addr,
  input  [15:0] io_in_din,
  output [15:0] io_in_dout,
  output        io_in_wait_n,
  output        io_in_valid,
  output        io_out_rd,
  output        io_out_wr,
  output [6:0]  io_out_addr,
  output [15:0] io_out_din,
  input  [15:0] io_out_dout,
  input         io_out_wait_n,
  input         io_out_valid
);

  reg         clear_s;
  reg         clear_REG;
  wire        clear = clear_s ^ clear_REG;
  reg         wait_n_enableReg;
  wire        wait_n = io_out_wait_n | wait_n_enableReg & ~clear;
  reg         valid_enableReg;
  wire        valid = io_out_valid | valid_enableReg & ~clear;
  reg  [15:0] data_dataReg;
  reg         data_enableReg;
  reg         pendingRead;
  reg         pendingWrite;
  reg         clearRead_REG;
  wire        clearRead = clear & clearRead_REG;
  wire        io_out_rd_0 = io_in_rd & (~pendingRead | clearRead);
  wire        io_out_wr_0 = io_in_wr & (~pendingWrite | clear);
  `ifndef SYNTHESIS
    always @(posedge clock) begin
      if ((`PRINTF_COND_) & ~reset)
        $fwrite(32'h80000002,
                "DataFreezer(read: %d, write: %d, wait: %d, valid: %d, clear: %d)\n",
                io_out_rd_0, io_out_wr_0, wait_n, valid, clear);
    end // always @(posedge)
  `endif // not def SYNTHESIS
  always @(posedge io_targetClock) begin
    if (reset)
      clear_s <= 1'h0;
    else
      clear_s <= ~clear_s;
  end // always @(posedge)
  always @(posedge clock) begin
    clear_REG <= clear_s;
    if (io_out_valid)
      data_dataReg <= io_out_dout;
    clearRead_REG <= valid;
    if (reset) begin
      wait_n_enableReg <= 1'h0;
      valid_enableReg <= 1'h0;
      data_enableReg <= 1'h0;
      pendingRead <= 1'h0;
      pendingWrite <= 1'h0;
    end
    else begin
      wait_n_enableReg <= io_out_wait_n | ~clear & wait_n_enableReg;
      valid_enableReg <= io_out_valid | ~clear & valid_enableReg;
      data_enableReg <= ~clear & (io_out_valid | data_enableReg);
      pendingRead <= io_in_rd & io_out_wait_n | ~clearRead & pendingRead;
      pendingWrite <= io_in_wr & io_out_wait_n | ~clear & pendingWrite;
    end
  end // always @(posedge)
  assign io_in_dout = data_enableReg & ~clear ? data_dataReg : io_out_dout;
  assign io_in_wait_n = wait_n;
  assign io_in_valid = valid;
  assign io_out_rd = io_out_rd_0;
  assign io_out_wr = io_out_wr_0;
  assign io_out_addr = io_in_addr;
  assign io_out_din = io_in_din;
endmodule

