module SDRAM(
  input         clock,
  input         reset,
  input         io_mem_rd,
  input         io_mem_wr,
  input  [24:0] io_mem_addr,
  input  [15:0] io_mem_din,
  output [15:0] io_mem_dout,
  output        io_mem_wait_n,
  output        io_mem_valid,
  output        io_mem_burstDone,
  output        io_sdram_cs_n,
  output        io_sdram_ras_n,
  output        io_sdram_cas_n,
  output        io_sdram_we_n,
  output        io_sdram_oe_n,
  output [1:0]  io_sdram_bank,
  output [12:0] io_sdram_addr,
  output [15:0] io_sdram_din,
  input  [15:0] io_sdram_dout
);

  reg  [2:0]  casez_tmp;
  reg  [2:0]  stateReg;
  reg  [3:0]  commandReg;
  wire        latch = stateReg != 3'h3 & casez_tmp == 3'h3;
  wire        isReadWrite = io_mem_rd | io_mem_wr;
  reg         requestReg_wr;
  reg  [1:0]  requestReg_addr_bank;
  reg  [8:0]  requestReg_addr_col;
  reg  [1:0]  bankReg;
  reg  [12:0] addrReg;
  reg  [15:0] dinReg;
  reg  [15:0] doutReg;
  reg  [14:0] waitCounter;
  wire        _io_debug_refresh_T = stateReg == 3'h6;
  wire        _refreshCounter_T_4 = waitCounter == 15'h0;
  reg  [9:0]  refreshCounter;
  wire        modeDone = waitCounter == 15'h1;
  wire        readDone = waitCounter == 15'h5;
  wire        writeDone = waitCounter == 15'h6;
  wire        triggerRefresh = refreshCounter > 10'h2EA;
  wire        _io_debug_idle_T = stateReg == 3'h2;
  wire        _io_debug_active_T = stateReg == 3'h3;
  wire        _io_debug_write_T = stateReg == 3'h5;
  reg         validReg;
  reg         memBurstDone_REG;
  wire        _GEN = waitCounter == 15'h4AFF;
  wire        _GEN_0 = waitCounter == 15'h4B0D;
  wire        _GEN_1 = waitCounter == 15'h4B01 | waitCounter == 15'h4B07;
  wire        _GEN_2 = _refreshCounter_T_4 | _GEN | _GEN_1 | ~_GEN_0;
  wire        _GEN_3 = triggerRefresh | ~isReadWrite;
  wire        _GEN_4 = ~readDone | _GEN_3;
  wire        _GEN_5 = ~writeDone | _GEN_3;
  wire        _GEN_6 = _io_debug_refresh_T & readDone & isReadWrite;
  reg  [3:0]  casez_tmp_0;
  wire [3:0]  _GEN_7 = _GEN_0 ? 4'h0 : 4'h7;
  wire [3:0]  _GEN_8 = _GEN_1 ? 4'h1 : _GEN_7;
  wire [3:0]  _GEN_9 = _GEN ? 4'h2 : _GEN_8;
  wire [3:0]  _GEN_10 = triggerRefresh ? 4'h1 : {1'h0, ~isReadWrite, 2'h3};
  wire [3:0]  _GEN_11 = {1'h0, ~_GEN_6, 2'h3};
  always @(*) begin
    casez (stateReg)
      3'b000:
        casez_tmp_0 = _refreshCounter_T_4 ? 4'h8 : _GEN_9;
      3'b001:
        casez_tmp_0 = 4'h7;
      3'b010:
        casez_tmp_0 = _GEN_10;
      3'b011:
        casez_tmp_0 = modeDone ? {3'h2, ~requestReg_wr} : 4'h7;
      3'b100:
        casez_tmp_0 = readDone ? _GEN_10 : 4'h7;
      3'b101:
        casez_tmp_0 = writeDone ? _GEN_10 : 4'h7;
      3'b110:
        casez_tmp_0 = _GEN_11;
      default:
        casez_tmp_0 = _GEN_11;
    endcase
  end // always @(*)
  reg  [12:0] casez_tmp_1;
  wire [12:0] _GEN_12 = _GEN_6 ? io_mem_addr[22:10] : addrReg;
  always @(*) begin
    casez (stateReg)
      3'b000:
        casez_tmp_1 = _GEN_2 ? 13'h400 : 13'h22;
      3'b001:
        casez_tmp_1 = addrReg;
      3'b010:
        casez_tmp_1 = _GEN_3 ? addrReg : io_mem_addr[22:10];
      3'b011:
        casez_tmp_1 = modeDone ? {4'h2, requestReg_addr_col} : addrReg;
      3'b100:
        casez_tmp_1 = _GEN_4 ? addrReg : io_mem_addr[22:10];
      3'b101:
        casez_tmp_1 = _GEN_5 ? addrReg : io_mem_addr[22:10];
      3'b110:
        casez_tmp_1 = _GEN_12;
      default:
        casez_tmp_1 = _GEN_12;
    endcase
  end // always @(*)
  wire [2:0]  _GEN_13 = isReadWrite ? 3'h3 : stateReg;
  wire [2:0]  _GEN_14 = {2'h1, isReadWrite};
  wire [2:0]  _GEN_15 = triggerRefresh ? 3'h6 : _GEN_14;
  wire [2:0]  _GEN_16 = _io_debug_refresh_T & readDone ? _GEN_14 : stateReg;
  always @(*) begin
    casez (stateReg)
      3'b000:
        casez_tmp = _GEN_2 ? stateReg : 3'h1;
      3'b001:
        casez_tmp = modeDone ? 3'h2 : stateReg;
      3'b010:
        casez_tmp = triggerRefresh ? 3'h6 : _GEN_13;
      3'b011:
        casez_tmp = modeDone ? {2'h2, requestReg_wr} : stateReg;
      3'b100:
        casez_tmp = readDone ? _GEN_15 : stateReg;
      3'b101:
        casez_tmp = writeDone ? _GEN_15 : stateReg;
      3'b110:
        casez_tmp = _GEN_16;
      default:
        casez_tmp = _GEN_16;
    endcase
  end // always @(*)
  wire        _io_debug_read_T = stateReg == 3'h4;
  always @(posedge clock) begin
    if (reset) begin
      stateReg <= 3'h0;
      commandReg <= 4'h7;
      waitCounter <= 15'h0;
      refreshCounter <= 10'h0;
      validReg <= 1'h0;
      memBurstDone_REG <= 1'h0;
    end
    else begin
      stateReg <= casez_tmp;
      commandReg <= casez_tmp_0;
      waitCounter <= casez_tmp == stateReg ? 15'(waitCounter + 15'h1) : 15'h0;
      if (_io_debug_refresh_T & _refreshCounter_T_4)
        refreshCounter <= 10'h0;
      else if ((|stateReg) & stateReg != 3'h1)
        refreshCounter <= 10'(refreshCounter + 10'h1);
      validReg <= _io_debug_read_T & (|(waitCounter[14:1]));
      memBurstDone_REG <= _io_debug_read_T & readDone;
    end
    if (latch) begin
      requestReg_wr <= io_mem_wr;
      requestReg_addr_bank <= io_mem_addr[24:23];
      requestReg_addr_col <= io_mem_addr[9:1];
    end
    if (~(stateReg == 3'h0 | stateReg == 3'h1)) begin
      if (_io_debug_idle_T) begin
        if (_GEN_3) begin
        end
        else
          bankReg <= io_mem_addr[24:23];
      end
      else if (_io_debug_active_T) begin
        if (modeDone)
          bankReg <= requestReg_addr_bank;
      end
      else if (_io_debug_read_T) begin
        if (_GEN_4) begin
        end
        else
          bankReg <= io_mem_addr[24:23];
      end
      else if (_io_debug_write_T) begin
        if (_GEN_5) begin
        end
        else
          bankReg <= io_mem_addr[24:23];
      end
      else if (_GEN_6)
        bankReg <= io_mem_addr[24:23];
    end
    addrReg <= casez_tmp_1;
    dinReg <= io_mem_din;
    doutReg <= io_sdram_dout;
  end // always @(posedge)
  assign io_mem_dout = doutReg;
  assign io_mem_wait_n =
    _io_debug_idle_T & ~isReadWrite | latch & io_mem_rd | _io_debug_active_T & modeDone
    & requestReg_wr | _io_debug_write_T & waitCounter < 15'h3;
  assign io_mem_valid = validReg;
  assign io_mem_burstDone = memBurstDone_REG | _io_debug_write_T & waitCounter == 15'h3;
  assign io_sdram_cs_n = commandReg[3];
  assign io_sdram_ras_n = commandReg[2];
  assign io_sdram_cas_n = commandReg[1];
  assign io_sdram_we_n = commandReg[0];
  assign io_sdram_oe_n = stateReg != 3'h4;
  assign io_sdram_bank = bankReg;
  assign io_sdram_addr = addrReg;
  assign io_sdram_din = dinReg;
endmodule

