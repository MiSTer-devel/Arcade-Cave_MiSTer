// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// Two-way write-back cache used by the EEPROM/NVRAM path.
module CaveNvramWriteBackCache(
  input         clock,
  input         reset,
  input         io_enable,
  input         io_in_rd,
  input         io_in_wr,
  input  [6:0]  io_in_addr,
  input  [15:0] io_in_din,
  output [15:0] io_in_dout,
  output        io_in_wait_n,
  output        io_in_valid,
  output        io_out_rd,
  output        io_out_wr,
  output [24:0] io_out_addr,
  output [15:0] io_out_din,
  input  [15:0] io_out_dout,
  input         io_out_wait_n,
  input         io_out_valid
);
  localparam [3:0] STATE_INIT       = 4'd0;
  localparam [3:0] STATE_IDLE       = 4'd1;
  localparam [3:0] STATE_CHECK      = 4'd2;
  localparam [3:0] STATE_FILL       = 4'd3;
  localparam [3:0] STATE_FILL_WAIT  = 4'd4;
  localparam [3:0] STATE_EVICT      = 4'd5;
  localparam [3:0] STATE_EVICT_WAIT = 4'd6;
  localparam [3:0] STATE_MERGE      = 4'd7;
  localparam [3:0] STATE_WRITE      = 4'd8;

  function [15:0] swap16;
    input [15:0] value;
    begin
      swap16 = {value[7:0], value[15:8]};
    end
  endfunction

  function [15:0] entry_word;
    input [69:0] entry;
    input [1:0] offset;
    begin
      case (offset)
        2'd0: entry_word = entry[15:0];
        2'd1: entry_word = entry[31:16];
        2'd2: entry_word = entry[47:32];
        default: entry_word = entry[63:48];
      endcase
    end
  endfunction

  function [69:0] pack_entry;
    input        valid;
    input        dirty;
    input [3:0]  tag;
    input [15:0] word3;
    input [15:0] word2;
    input [15:0] word1;
    input [15:0] word0;
    begin
      pack_entry = {valid, dirty, tag, word3, word2, word1, word0};
    end
  endfunction

  function [1:0] set_lru;
    input [1:0] lru;
    input       index;
    input       value;
    reg [1:0] mask;
    begin
      mask = 2'b01 << index;
      set_lru = value ? (lru | mask) : (lru & ~mask);
    end
  endfunction

  reg  [3:0]  stateReg;
  reg  [1:0]  offsetReg;
  reg         requestReg_rd;
  reg         requestReg_wr;
  reg  [3:0]  requestReg_addr_tag;
  reg         requestReg_addr_index;
  reg  [1:0]  requestReg_addr_offset;
  reg  [15:0] requestReg_din;
  reg  [15:0] doutReg;
  reg         validReg;
  reg  [1:0]  lruReg;
  reg         wayReg;
  reg         initCounter;
  reg  [1:0]  burstCounter;

  reg         cacheEntryReg_valid;
  reg         cacheEntryReg_dirty;
  reg  [3:0]  cacheEntryReg_tag;
  reg  [15:0] cacheEntryReg_line_words_0;
  reg  [15:0] cacheEntryReg_line_words_1;
  reg  [15:0] cacheEntryReg_line_words_2;
  reg  [15:0] cacheEntryReg_line_words_3;

  wire        idle = stateReg == STATE_IDLE;
  wire        check = stateReg == STATE_CHECK;
  wire        fill = stateReg == STATE_FILL;
  wire        fillWait = stateReg == STATE_FILL_WAIT;
  wire        evict = stateReg == STATE_EVICT;
  wire        evictWait = stateReg == STATE_EVICT_WAIT;
  wire        merge = stateReg == STATE_MERGE;
  wire        write = stateReg == STATE_WRITE;

  wire [3:0]  inAddrTag = {1'b0, io_in_addr[6:4]};
  wire        inAddrIndex = io_in_addr[3];
  wire [1:0]  inAddrOffset = io_in_addr[2:1];
  wire        start = io_enable & (io_in_rd | io_in_wr) & idle;

  wire [69:0] wayAReadData;
  wire [69:0] wayBReadData;
  wire        hitA = wayAReadData[69] & (wayAReadData[67:64] == requestReg_addr_tag);
  wire        hitB = wayBReadData[69] & (wayBReadData[67:64] == requestReg_addr_tag);
  wire        hit = hitA | hitB;
  wire        dirtyA = wayAReadData[68] & (wayAReadData[67:64] != requestReg_addr_tag);
  wire        dirtyB = wayBReadData[68] & (wayBReadData[67:64] != requestReg_addr_tag);
  wire        dirty = (~wayReg & dirtyA) | (wayReg & dirtyB);
  wire        selectedWay = (check & hit) ? ~hitA : (start ? lruReg[inAddrIndex] : wayReg);
  wire [69:0] selectedEntry = selectedWay ? wayBReadData : wayAReadData;

  wire [1:0]  fillWordIndex = requestReg_addr_offset + burstCounter;
  wire        fillUpdatesWord0 = fillWordIndex == 2'd0;
  wire        fillUpdatesWord1 = fillWordIndex == 2'd1;
  wire        fillUpdatesWord2 = fillWordIndex == 2'd2;
  wire        fillUpdatesWord3 = fillWordIndex == 2'd3;
  wire [15:0] cacheEntryWordAtOffset = entry_word(
    pack_entry(
      cacheEntryReg_valid,
      cacheEntryReg_dirty,
      cacheEntryReg_tag,
      cacheEntryReg_line_words_3,
      cacheEntryReg_line_words_2,
      cacheEntryReg_line_words_1,
      cacheEntryReg_line_words_0
    ),
    offsetReg
  );
  wire [15:0] fillWordAtOffset =
    offsetReg == fillWordIndex ? io_out_dout : cacheEntryWordAtOffset;
  wire [15:0] evictWord = entry_word(
    pack_entry(
      cacheEntryReg_valid,
      cacheEntryReg_dirty,
      cacheEntryReg_tag,
      cacheEntryReg_line_words_3,
      cacheEntryReg_line_words_2,
      cacheEntryReg_line_words_1,
      cacheEntryReg_line_words_0
    ),
    burstCounter
  );

  wire        burstCounterEnable = (fillWait & io_out_valid) | ((evict | evictWait) & io_out_wait_n);
  wire        burstCounterWrap = burstCounterEnable & (&burstCounter);
  wire        readHit = check & hit & requestReg_rd;
  wire        fillWordValid = fillWait & io_out_valid;

  always @(posedge clock) begin
    if (reset) begin
      stateReg <= STATE_INIT;
      initCounter <= 1'b0;
      burstCounter <= 2'b00;
    end
    else begin
      case (stateReg)
        STATE_INIT: begin
          if (initCounter)
            stateReg <= STATE_IDLE;
          initCounter <= initCounter - 1'b1;
        end

        STATE_IDLE: begin
          if (start)
            stateReg <= STATE_CHECK;
        end

        STATE_CHECK: begin
          if (hit)
            stateReg <= requestReg_rd ? STATE_IDLE : STATE_MERGE;
          else if (dirty)
            stateReg <= STATE_EVICT;
          else
            stateReg <= STATE_FILL;
        end

        STATE_FILL: begin
          if (io_out_wait_n)
            stateReg <= STATE_FILL_WAIT;
        end

        STATE_FILL_WAIT: begin
          if (burstCounterWrap)
            stateReg <= requestReg_wr ? STATE_MERGE : STATE_WRITE;
        end

        STATE_EVICT: begin
          if (io_out_wait_n)
            stateReg <= STATE_EVICT_WAIT;
        end

        STATE_EVICT_WAIT: begin
          if (burstCounterWrap)
            stateReg <= STATE_FILL;
        end

        STATE_MERGE:
          stateReg <= STATE_WRITE;

        STATE_WRITE:
          stateReg <= STATE_IDLE;

        default:
          stateReg <= STATE_INIT;
      endcase

      if (burstCounterEnable)
        burstCounter <= burstCounter + 2'b01;
    end

    if (start) begin
      offsetReg <= inAddrOffset;
      requestReg_rd <= io_in_rd;
      requestReg_wr <= io_in_wr;
      requestReg_addr_tag <= inAddrTag;
      requestReg_addr_index <= inAddrIndex;
      requestReg_addr_offset <= inAddrOffset;
      requestReg_din <= io_in_din;
    end

    if (readHit)
      doutReg <= hitA ? swap16(entry_word(wayAReadData, offsetReg)) : swap16(entry_word(wayBReadData, offsetReg));
    else if (fillWordValid)
      doutReg <= swap16(fillWordAtOffset);

    validReg <= readHit | (fillWordValid & requestReg_rd & (burstCounter == 2'b00));

    if (check) begin
      if (hit)
        lruReg <= set_lru(lruReg, requestReg_addr_index, hitA);
      else
        lruReg <= set_lru(lruReg, requestReg_addr_index, ~wayReg);
    end

    if (start)
      wayReg <= lruReg[inAddrIndex];
    else if (check & hit)
      wayReg <= ~hitA;

    if (merge) begin
      case (offsetReg)
        2'd0: cacheEntryReg_line_words_0 <= swap16(requestReg_din);
        2'd1: cacheEntryReg_line_words_1 <= swap16(requestReg_din);
        2'd2: cacheEntryReg_line_words_2 <= swap16(requestReg_din);
        default: cacheEntryReg_line_words_3 <= swap16(requestReg_din);
      endcase
    end
    else begin
      if (fillWordValid) begin
        cacheEntryReg_valid <= 1'b1;
        cacheEntryReg_tag <= requestReg_addr_tag;
        if (fillUpdatesWord0)
          cacheEntryReg_line_words_0 <= io_out_dout;
        if (fillUpdatesWord1)
          cacheEntryReg_line_words_1 <= io_out_dout;
        if (fillUpdatesWord2)
          cacheEntryReg_line_words_2 <= io_out_dout;
        if (fillUpdatesWord3)
          cacheEntryReg_line_words_3 <= io_out_dout;
      end
      else if (check) begin
        cacheEntryReg_valid <= selectedEntry[69];
        cacheEntryReg_tag <= selectedEntry[67:64];
        cacheEntryReg_line_words_0 <= selectedEntry[15:0];
        cacheEntryReg_line_words_1 <= selectedEntry[31:16];
        cacheEntryReg_line_words_2 <= selectedEntry[47:32];
        cacheEntryReg_line_words_3 <= selectedEntry[63:48];
      end
    end

    if (merge)
      cacheEntryReg_dirty <= 1'b1;
    else if (check)
      cacheEntryReg_dirty <= selectedEntry[68];
  end

  wire [69:0] nextCacheEntry = write
    ? pack_entry(
        cacheEntryReg_valid,
        cacheEntryReg_dirty,
        cacheEntryReg_tag,
        cacheEntryReg_line_words_3,
        cacheEntryReg_line_words_2,
        cacheEntryReg_line_words_1,
        cacheEntryReg_line_words_0
      )
    : 70'b0;

  wire wayAWriteEnable = (stateReg == STATE_INIT) | (write & ~wayReg);
  wire wayBWriteEnable = (stateReg == STATE_INIT) | (write & wayReg);

  CaveSyncReadMem #(
    .ADDR_WIDTH (1),
    .DATA_WIDTH (70),
    .DEPTH      (2)
  ) cacheEntryMemA_ext (
    .read_addr  (io_in_addr[3]),
    .read_en    (1'b1),
    .read_clk   (clock),
    .read_data  (wayAReadData),
    .write_addr (requestReg_addr_index),
    .write_en   (wayAWriteEnable),
    .write_clk  (clock),
    .write_data (nextCacheEntry)
  );

  CaveSyncReadMem #(
    .ADDR_WIDTH (1),
    .DATA_WIDTH (70),
    .DEPTH      (2)
  ) cacheEntryMemB_ext (
    .read_addr  (io_in_addr[3]),
    .read_en    (1'b1),
    .read_clk   (clock),
    .read_data  (wayBReadData),
    .write_addr (requestReg_addr_index),
    .write_en   (wayBWriteEnable),
    .write_clk  (clock),
    .write_data (nextCacheEntry)
  );

  wire [3:0] outAddrTag = io_out_rd ? requestReg_addr_tag : cacheEntryReg_tag;
  wire [1:0] outAddrOffset = io_out_rd ? requestReg_addr_offset : 2'b00;

  assign io_in_dout = doutReg;
  assign io_in_wait_n = io_enable & idle;
  assign io_in_valid = validReg;

  assign io_out_rd = fill;
  assign io_out_wr = evict | evictWait;
  assign io_out_addr = {17'b0, outAddrTag, requestReg_addr_index, outAddrOffset, 1'b0};
  assign io_out_din = evictWord;
endmodule
