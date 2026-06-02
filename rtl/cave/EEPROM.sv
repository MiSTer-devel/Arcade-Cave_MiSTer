// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module EEPROM(
  input         clock,
  input         reset,
  output        io_mem_rd,
  output        io_mem_wr,
  output [6:0]  io_mem_addr,
  output [15:0] io_mem_din,
  input  [15:0] io_mem_dout,
  input         io_mem_wait_n,
  input         io_mem_valid,
  input         io_serial_cs,
  input         io_serial_sck,
  input         io_serial_sdi,
  output        io_serial_sdo
);
  localparam [2:0] STATE_IDLE      = 3'd0;
  localparam [2:0] STATE_START     = 3'd1;
  localparam [2:0] STATE_COMMAND   = 3'd2;
  localparam [2:0] STATE_READ      = 3'd3;
  localparam [2:0] STATE_READ_WAIT = 3'd4;
  localparam [2:0] STATE_WRITE     = 3'd5;
  localparam [2:0] STATE_SHIFT_IN  = 3'd6;
  localparam [2:0] STATE_SHIFT_OUT = 3'd7;

  reg  [2:0]  stateReg;
  reg  [16:0] counterReg;
  reg  [5:0]  addrReg;
  reg  [15:0] dataReg;
  reg  [1:0]  opcodeReg;
  reg         serialReg;
  reg         writeAllReg;
  reg         writeEnableReg;
  reg         sckPrev;

  reg  [2:0]  stateNext;
  reg  [16:0] counterNext;
  reg  [5:0]  addrNext;
  reg  [15:0] dataNext;
  reg  [1:0]  opcodeNext;
  reg         serialNext;
  reg         writeAllNext;
  reg         writeEnableNext;

  wire sckRising = io_serial_sck & ~sckPrev;
  wire commandDone = counterReg[0];

  wire readCommand = opcodeReg == 2'd2;
  wire writeCommand = opcodeReg == 2'd1 && writeEnableReg;
  wire eraseCommand = opcodeReg == 2'd3 && writeEnableReg;
  wire writeAllCommand = opcodeReg == 2'd0 && addrReg[4:3] == 2'd1 && writeEnableReg;
  wire eraseAllCommand = opcodeReg == 2'd0 && addrReg[4:3] == 2'd2 && writeEnableReg;
  wire enableWriteCommand = opcodeReg == 2'd0 && addrReg[4:3] == 2'd3;
  wire disableWriteCommand = opcodeReg == 2'd0 && addrReg[4:3] == 2'd0;

  always @(*) begin
    stateNext = stateReg;
    counterNext = counterReg;
    addrNext = addrReg;
    dataNext = dataReg;
    opcodeNext = opcodeReg;
    serialNext = serialReg;
    writeAllNext = writeAllReg;
    writeEnableNext = writeEnableReg;

    if ((stateReg == STATE_COMMAND || stateReg == STATE_SHIFT_IN || stateReg == STATE_SHIFT_OUT)
        && sckRising)
      counterNext = {1'b0, counterReg[16:1]};

    case (stateReg)
      STATE_IDLE: begin
        counterNext = 17'h00080;
        addrNext = 6'h00;
        dataNext = 16'hFFFF;
        serialNext = 1'b1;
        writeAllNext = 1'b0;
        if (io_serial_cs)
          stateNext = STATE_START;
      end

      STATE_START: begin
        if (sckRising && io_serial_sdi)
          stateNext = STATE_COMMAND;
      end

      STATE_COMMAND: begin
        if (sckRising) begin
          opcodeNext = addrReg[5:4];
          addrNext = {addrReg[4:0], io_serial_sdi};

          if (commandDone) begin
            if (readCommand) begin
              counterNext = 17'h10000;
              serialNext = 1'b0;
              stateNext = STATE_READ;
            end
            else if (writeCommand) begin
              counterNext = 17'h08000;
              stateNext = STATE_SHIFT_IN;
            end
            else if (eraseCommand) begin
              stateNext = STATE_WRITE;
            end
            else if (writeAllCommand) begin
              counterNext = 17'h08000;
              addrNext = 6'h00;
              writeAllNext = 1'b1;
              stateNext = STATE_SHIFT_IN;
            end
            else if (eraseAllCommand) begin
              addrNext = 6'h00;
              writeAllNext = 1'b1;
              stateNext = STATE_WRITE;
            end
            else if (enableWriteCommand) begin
              writeEnableNext = 1'b1;
              stateNext = STATE_IDLE;
            end
            else if (disableWriteCommand) begin
              writeEnableNext = 1'b0;
              stateNext = STATE_IDLE;
            end
            else begin
              stateNext = STATE_IDLE;
            end
          end
        end
      end

      STATE_READ: begin
        if (io_mem_valid) begin
          dataNext = io_mem_dout;
          stateNext = STATE_SHIFT_OUT;
        end
        else if (io_mem_wait_n) begin
          stateNext = STATE_READ_WAIT;
        end
      end

      STATE_READ_WAIT: begin
        if (io_mem_valid) begin
          dataNext = io_mem_dout;
          stateNext = STATE_SHIFT_OUT;
        end
      end

      STATE_WRITE: begin
        if (io_mem_wait_n) begin
          addrNext = addrReg + 6'h01;
          if (!writeAllReg || &addrReg)
            stateNext = STATE_IDLE;
        end
      end

      STATE_SHIFT_IN: begin
        if (sckRising) begin
          serialNext = 1'b0;
          dataNext = {dataReg[14:0], io_serial_sdi};
          if (commandDone)
            stateNext = STATE_WRITE;
        end
      end

      STATE_SHIFT_OUT: begin
        if (sckRising) begin
          dataNext = {dataReg[14:0], 1'b0};
          serialNext = dataReg[15];
          if (commandDone)
            stateNext = STATE_IDLE;
        end
      end
    endcase

    if (!io_serial_cs)
      stateNext = STATE_IDLE;
  end

  always @(posedge clock) begin
    if (reset) begin
      stateReg <= STATE_IDLE;
      counterReg <= 17'h00000;
      addrReg <= 6'h00;
      dataReg <= 16'hFFFF;
      opcodeReg <= 2'h0;
      serialReg <= 1'b1;
      writeAllReg <= 1'b0;
      writeEnableReg <= 1'b0;
      sckPrev <= 1'b0;
    end
    else begin
      stateReg <= stateNext;
      counterReg <= counterNext;
      addrReg <= addrNext;
      dataReg <= dataNext;
      opcodeReg <= opcodeNext;
      serialReg <= serialNext;
      writeAllReg <= writeAllNext;
      writeEnableReg <= writeEnableNext;
      sckPrev <= io_serial_sck;
    end
  end

  assign io_mem_rd = stateReg == STATE_READ;
  assign io_mem_wr = stateReg == STATE_WRITE;
  assign io_mem_addr = {addrReg, 1'b0};
  assign io_mem_din = dataReg;
  assign io_serial_sdo = serialReg;
endmodule
