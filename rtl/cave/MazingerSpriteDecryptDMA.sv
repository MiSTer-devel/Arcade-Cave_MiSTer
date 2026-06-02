// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module MazingerSpriteDecryptDMA(
  input         clock,
  input         reset,
  input         io_start,
  output        io_busy,
  input  [31:0] io_src_base,
  input  [31:0] io_dst_base,
  output        io_mem_rd,
  output        io_mem_wr,
  output [31:0] io_mem_addr,
  output [7:0]  io_mem_mask,
  output [63:0] io_mem_din,
  input  [63:0] io_mem_dout,
  input         io_mem_wait_n,
  input         io_mem_valid,
  output [7:0]  io_mem_burstLength,
  input         io_mem_burstDone
);
  localparam [18:0] LAST_WORD     = 19'h7ffff;
  localparam [23:0] RAW_ROM_BYTES = 24'h280000;

  localparam [2:0] STATE_IDLE       = 3'd0;
  localparam [2:0] STATE_READ       = 3'd1;
  localparam [2:0] STATE_READ_WAIT  = 3'd2;
  localparam [2:0] STATE_FILL       = 3'd3;
  localparam [2:0] STATE_WRITE      = 3'd4;
  localparam [2:0] STATE_WRITE_WAIT = 3'd5;

  reg [2:0]  stateReg;
  reg [18:0] wordCounter;
  reg [2:0]  byteCounter;
  reg [63:0] outWordReg;
  reg [31:0] readAddrReg;

  function automatic [23:0] decrypt_byte_addr(input [21:0] dest_byte_addr);
    reg [23:0] decryptIndex;
    begin
      decryptIndex = {2'b00, dest_byte_addr} ^ 24'h00df88;
      decrypt_byte_addr = {
        decryptIndex[23],
        decryptIndex[22],
        decryptIndex[21],
        decryptIndex[20],
        decryptIndex[19],
        decryptIndex[9],
        decryptIndex[7],
        decryptIndex[3],
        decryptIndex[15],
        decryptIndex[4],
        decryptIndex[17],
        decryptIndex[14],
        decryptIndex[18],
        decryptIndex[2],
        decryptIndex[16],
        decryptIndex[5],
        decryptIndex[11],
        decryptIndex[8],
        decryptIndex[6],
        decryptIndex[13],
        decryptIndex[1],
        decryptIndex[10],
        decryptIndex[12],
        decryptIndex[0]
      };
    end
  endfunction

  wire [2:0]  nextByteCounter = byteCounter + 3'd1;
  wire [18:0] nextWordCounter = wordCounter + 19'd1;
  wire [23:0] mazingerFirstRawAddr = decrypt_byte_addr(22'd0);
  wire [23:0] mazingerNextByteRawAddr =
    decrypt_byte_addr({wordCounter, nextByteCounter});
  wire [23:0] mazingerNextWordRawAddr =
    decrypt_byte_addr({nextWordCounter, 3'b000});
  wire firstByteInRom = mazingerFirstRawAddr < RAW_ROM_BYTES;
  wire nextByteInRom = mazingerNextByteRawAddr < RAW_ROM_BYTES;
  wire nextWordInRom = mazingerNextWordRawAddr < RAW_ROM_BYTES;
  wire wordDone = byteCounter == 3'd7;
  wire dmaDone = wordCounter == LAST_WORD;

  function automatic [7:0] select_byte(input [63:0] data, input [2:0] index);
    begin
      case (index)
        3'd0: select_byte = data[7:0];
        3'd1: select_byte = data[15:8];
        3'd2: select_byte = data[23:16];
        3'd3: select_byte = data[31:24];
        3'd4: select_byte = data[39:32];
        3'd5: select_byte = data[47:40];
        3'd6: select_byte = data[55:48];
        default: select_byte = data[63:56];
      endcase
    end
  endfunction

  function automatic [63:0] put_byte(
    input [63:0] data,
    input [2:0] index,
    input [7:0] value
  );
    begin
      put_byte = data;
      case (index)
        3'd0: put_byte[7:0] = value;
        3'd1: put_byte[15:8] = value;
        3'd2: put_byte[23:16] = value;
        3'd3: put_byte[31:24] = value;
        3'd4: put_byte[39:32] = value;
        3'd5: put_byte[47:40] = value;
        3'd6: put_byte[55:48] = value;
        default: put_byte[63:56] = value;
      endcase
    end
  endfunction

  wire [7:0] fetchedByte = select_byte(io_mem_dout, readAddrReg[2:0]);
  wire [63:0] nextOutWordFromRead = put_byte(outWordReg, byteCounter, fetchedByte);
  wire [63:0] nextOutWordFromFill = put_byte(outWordReg, byteCounter, 8'hff);

  always @(posedge clock) begin
    if (reset) begin
      stateReg <= STATE_IDLE;
      wordCounter <= 19'd0;
      byteCounter <= 3'd0;
      outWordReg <= 64'd0;
      readAddrReg <= 32'd0;
    end
    else begin
      case (stateReg)
        STATE_IDLE: begin
          if (io_start) begin
            stateReg <= firstByteInRom ? STATE_READ : STATE_FILL;
            wordCounter <= 19'd0;
            byteCounter <= 3'd0;
            outWordReg <= 64'd0;
            readAddrReg <= io_src_base + {8'h00, mazingerFirstRawAddr};
          end
        end

        STATE_READ: begin
          if (io_mem_wait_n)
            stateReg <= STATE_READ_WAIT;
        end

        STATE_READ_WAIT: begin
          if (io_mem_valid) begin
            outWordReg <= nextOutWordFromRead;
            if (wordDone)
              stateReg <= STATE_WRITE;
            else begin
              byteCounter <= nextByteCounter;
              stateReg <= nextByteInRom ? STATE_READ : STATE_FILL;
              readAddrReg <= io_src_base + {8'h00, mazingerNextByteRawAddr};
            end
          end
        end

        STATE_FILL: begin
          outWordReg <= nextOutWordFromFill;
          if (wordDone)
            stateReg <= STATE_WRITE;
          else begin
            byteCounter <= nextByteCounter;
            stateReg <= nextByteInRom ? STATE_READ : STATE_FILL;
            readAddrReg <= io_src_base + {8'h00, mazingerNextByteRawAddr};
          end
        end

        STATE_WRITE: begin
          if (io_mem_wait_n) begin
            if (dmaDone)
              stateReg <= STATE_IDLE;
            else begin
              stateReg <= nextWordInRom ? STATE_READ : STATE_FILL;
              wordCounter <= nextWordCounter;
              byteCounter <= 3'd0;
              outWordReg <= 64'd0;
              readAddrReg <= io_src_base + {8'h00, mazingerNextWordRawAddr};
            end
          end
        end

        STATE_WRITE_WAIT: begin
          if (io_mem_burstDone) begin
            if (dmaDone)
              stateReg <= STATE_IDLE;
            else begin
              stateReg <= nextWordInRom ? STATE_READ : STATE_FILL;
              wordCounter <= nextWordCounter;
              byteCounter <= 3'd0;
              outWordReg <= 64'd0;
              readAddrReg <= io_src_base + {8'h00, mazingerNextWordRawAddr};
            end
          end
        end

        default: begin
          stateReg <= STATE_IDLE;
        end
      endcase
    end
  end

  assign io_busy = stateReg != STATE_IDLE;
  assign io_mem_rd = stateReg == STATE_READ;
  assign io_mem_wr = stateReg == STATE_WRITE;
  assign io_mem_addr =
    (stateReg == STATE_READ)
      ? readAddrReg
      : (io_dst_base + {10'h000, wordCounter, 3'b000});
  assign io_mem_mask = 8'hff;
  assign io_mem_din = outWordReg;
  assign io_mem_burstLength = 8'd1;
endmodule
