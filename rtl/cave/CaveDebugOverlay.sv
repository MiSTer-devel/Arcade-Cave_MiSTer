// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

module CaveDebugOverlay(
  input  [8:0]  io_video_pos_x,
  input  [8:0]  io_video_pos_y,
  input  [2:0]  io_debug_view,
  input  [63:0] io_debug_bits,
  output [23:0] io_rgb
);
  localparam [5:0] DEBUG_CH_SPACE = 6'd16;
  localparam [5:0] DEBUG_CH_M     = 6'd17;
  localparam [5:0] DEBUG_CH_R     = 6'd18;
  localparam [5:0] DEBUG_CH_P     = 6'd19;
  localparam [5:0] DEBUG_CH_L     = 6'd20;
  localparam [5:0] DEBUG_CH_N     = 6'd21;
  localparam [5:0] DEBUG_CH_K     = 6'd22;
  localparam [5:0] DEBUG_CH_H     = 6'd23;
  localparam [5:0] DEBUG_CH_S     = 6'd24;
  localparam [5:0] DEBUG_CH_T     = 6'd25;

  function automatic [4:0] debugGlyph5x7;
    input [5:0] ch;
    input [2:0] row;
    begin
      case (ch)
        6'd0: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b01110;
            3'd1: debugGlyph5x7 = 5'b10001;
            3'd2: debugGlyph5x7 = 5'b10011;
            3'd3: debugGlyph5x7 = 5'b10101;
            3'd4: debugGlyph5x7 = 5'b11001;
            3'd5: debugGlyph5x7 = 5'b10001;
            default: debugGlyph5x7 = 5'b01110;
          endcase
        end
        6'd1: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b00100;
            3'd1: debugGlyph5x7 = 5'b01100;
            3'd2: debugGlyph5x7 = 5'b00100;
            3'd3: debugGlyph5x7 = 5'b00100;
            3'd4: debugGlyph5x7 = 5'b00100;
            3'd5: debugGlyph5x7 = 5'b00100;
            default: debugGlyph5x7 = 5'b01110;
          endcase
        end
        6'd2: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b11110;
            3'd1: debugGlyph5x7 = 5'b00001;
            3'd2: debugGlyph5x7 = 5'b00001;
            3'd3: debugGlyph5x7 = 5'b01110;
            3'd4: debugGlyph5x7 = 5'b10000;
            3'd5: debugGlyph5x7 = 5'b10000;
            default: debugGlyph5x7 = 5'b11111;
          endcase
        end
        6'd3: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b11110;
            3'd1: debugGlyph5x7 = 5'b00001;
            3'd2: debugGlyph5x7 = 5'b00001;
            3'd3: debugGlyph5x7 = 5'b01110;
            3'd4: debugGlyph5x7 = 5'b00001;
            3'd5: debugGlyph5x7 = 5'b00001;
            default: debugGlyph5x7 = 5'b11110;
          endcase
        end
        6'd4: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b10010;
            3'd1: debugGlyph5x7 = 5'b10010;
            3'd2: debugGlyph5x7 = 5'b10010;
            3'd3: debugGlyph5x7 = 5'b11111;
            3'd4: debugGlyph5x7 = 5'b00010;
            3'd5: debugGlyph5x7 = 5'b00010;
            default: debugGlyph5x7 = 5'b00010;
          endcase
        end
        6'd5: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b11111;
            3'd1: debugGlyph5x7 = 5'b10000;
            3'd2: debugGlyph5x7 = 5'b10000;
            3'd3: debugGlyph5x7 = 5'b11110;
            3'd4: debugGlyph5x7 = 5'b00001;
            3'd5: debugGlyph5x7 = 5'b00001;
            default: debugGlyph5x7 = 5'b11110;
          endcase
        end
        6'd6: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b01110;
            3'd1: debugGlyph5x7 = 5'b10000;
            3'd2: debugGlyph5x7 = 5'b10000;
            3'd3: debugGlyph5x7 = 5'b11110;
            3'd4: debugGlyph5x7 = 5'b10001;
            3'd5: debugGlyph5x7 = 5'b10001;
            default: debugGlyph5x7 = 5'b01110;
          endcase
        end
        6'd7: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b11111;
            3'd1: debugGlyph5x7 = 5'b00001;
            3'd2: debugGlyph5x7 = 5'b00010;
            3'd3: debugGlyph5x7 = 5'b00100;
            3'd4: debugGlyph5x7 = 5'b01000;
            3'd5: debugGlyph5x7 = 5'b01000;
            default: debugGlyph5x7 = 5'b01000;
          endcase
        end
        6'd8: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b01110;
            3'd1: debugGlyph5x7 = 5'b10001;
            3'd2: debugGlyph5x7 = 5'b10001;
            3'd3: debugGlyph5x7 = 5'b01110;
            3'd4: debugGlyph5x7 = 5'b10001;
            3'd5: debugGlyph5x7 = 5'b10001;
            default: debugGlyph5x7 = 5'b01110;
          endcase
        end
        6'd9: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b01110;
            3'd1: debugGlyph5x7 = 5'b10001;
            3'd2: debugGlyph5x7 = 5'b10001;
            3'd3: debugGlyph5x7 = 5'b01111;
            3'd4: debugGlyph5x7 = 5'b00001;
            3'd5: debugGlyph5x7 = 5'b00001;
            default: debugGlyph5x7 = 5'b01110;
          endcase
        end
        6'd10: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b01110;
            3'd1: debugGlyph5x7 = 5'b10001;
            3'd2: debugGlyph5x7 = 5'b10001;
            3'd3: debugGlyph5x7 = 5'b11111;
            3'd4: debugGlyph5x7 = 5'b10001;
            3'd5: debugGlyph5x7 = 5'b10001;
            default: debugGlyph5x7 = 5'b10001;
          endcase
        end
        6'd11: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b11110;
            3'd1: debugGlyph5x7 = 5'b10001;
            3'd2: debugGlyph5x7 = 5'b10001;
            3'd3: debugGlyph5x7 = 5'b11110;
            3'd4: debugGlyph5x7 = 5'b10001;
            3'd5: debugGlyph5x7 = 5'b10001;
            default: debugGlyph5x7 = 5'b11110;
          endcase
        end
        6'd12: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b01111;
            3'd1: debugGlyph5x7 = 5'b10000;
            3'd2: debugGlyph5x7 = 5'b10000;
            3'd3: debugGlyph5x7 = 5'b10000;
            3'd4: debugGlyph5x7 = 5'b10000;
            3'd5: debugGlyph5x7 = 5'b10000;
            default: debugGlyph5x7 = 5'b01111;
          endcase
        end
        6'd13: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b11110;
            3'd1: debugGlyph5x7 = 5'b10001;
            3'd2: debugGlyph5x7 = 5'b10001;
            3'd3: debugGlyph5x7 = 5'b10001;
            3'd4: debugGlyph5x7 = 5'b10001;
            3'd5: debugGlyph5x7 = 5'b10001;
            default: debugGlyph5x7 = 5'b11110;
          endcase
        end
        6'd14: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b11111;
            3'd1: debugGlyph5x7 = 5'b10000;
            3'd2: debugGlyph5x7 = 5'b10000;
            3'd3: debugGlyph5x7 = 5'b11110;
            3'd4: debugGlyph5x7 = 5'b10000;
            3'd5: debugGlyph5x7 = 5'b10000;
            default: debugGlyph5x7 = 5'b11111;
          endcase
        end
        6'd15: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b11111;
            3'd1: debugGlyph5x7 = 5'b10000;
            3'd2: debugGlyph5x7 = 5'b10000;
            3'd3: debugGlyph5x7 = 5'b11110;
            3'd4: debugGlyph5x7 = 5'b10000;
            3'd5: debugGlyph5x7 = 5'b10000;
            default: debugGlyph5x7 = 5'b10000;
          endcase
        end
        DEBUG_CH_M: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b10001;
            3'd1: debugGlyph5x7 = 5'b11011;
            3'd2: debugGlyph5x7 = 5'b10101;
            3'd3: debugGlyph5x7 = 5'b10101;
            3'd4: debugGlyph5x7 = 5'b10001;
            3'd5: debugGlyph5x7 = 5'b10001;
            default: debugGlyph5x7 = 5'b10001;
          endcase
        end
        DEBUG_CH_R: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b11110;
            3'd1: debugGlyph5x7 = 5'b10001;
            3'd2: debugGlyph5x7 = 5'b10001;
            3'd3: debugGlyph5x7 = 5'b11110;
            3'd4: debugGlyph5x7 = 5'b10100;
            3'd5: debugGlyph5x7 = 5'b10010;
            default: debugGlyph5x7 = 5'b10001;
          endcase
        end
        DEBUG_CH_P: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b11110;
            3'd1: debugGlyph5x7 = 5'b10001;
            3'd2: debugGlyph5x7 = 5'b10001;
            3'd3: debugGlyph5x7 = 5'b11110;
            3'd4: debugGlyph5x7 = 5'b10000;
            3'd5: debugGlyph5x7 = 5'b10000;
            default: debugGlyph5x7 = 5'b10000;
          endcase
        end
        DEBUG_CH_L: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b10000;
            3'd1: debugGlyph5x7 = 5'b10000;
            3'd2: debugGlyph5x7 = 5'b10000;
            3'd3: debugGlyph5x7 = 5'b10000;
            3'd4: debugGlyph5x7 = 5'b10000;
            3'd5: debugGlyph5x7 = 5'b10000;
            default: debugGlyph5x7 = 5'b11111;
          endcase
        end
        DEBUG_CH_N: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b10001;
            3'd1: debugGlyph5x7 = 5'b11001;
            3'd2: debugGlyph5x7 = 5'b10101;
            3'd3: debugGlyph5x7 = 5'b10011;
            3'd4: debugGlyph5x7 = 5'b10001;
            3'd5: debugGlyph5x7 = 5'b10001;
            default: debugGlyph5x7 = 5'b10001;
          endcase
        end
        DEBUG_CH_K: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b10001;
            3'd1: debugGlyph5x7 = 5'b10010;
            3'd2: debugGlyph5x7 = 5'b10100;
            3'd3: debugGlyph5x7 = 5'b11000;
            3'd4: debugGlyph5x7 = 5'b10100;
            3'd5: debugGlyph5x7 = 5'b10010;
            default: debugGlyph5x7 = 5'b10001;
          endcase
        end
        DEBUG_CH_H: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b10001;
            3'd1: debugGlyph5x7 = 5'b10001;
            3'd2: debugGlyph5x7 = 5'b10001;
            3'd3: debugGlyph5x7 = 5'b11111;
            3'd4: debugGlyph5x7 = 5'b10001;
            3'd5: debugGlyph5x7 = 5'b10001;
            default: debugGlyph5x7 = 5'b10001;
          endcase
        end
        DEBUG_CH_S: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b01111;
            3'd1: debugGlyph5x7 = 5'b10000;
            3'd2: debugGlyph5x7 = 5'b10000;
            3'd3: debugGlyph5x7 = 5'b01110;
            3'd4: debugGlyph5x7 = 5'b00001;
            3'd5: debugGlyph5x7 = 5'b00001;
            default: debugGlyph5x7 = 5'b11110;
          endcase
        end
        DEBUG_CH_T: begin
          case (row)
            3'd0: debugGlyph5x7 = 5'b11111;
            3'd1: debugGlyph5x7 = 5'b00100;
            3'd2: debugGlyph5x7 = 5'b00100;
            3'd3: debugGlyph5x7 = 5'b00100;
            3'd4: debugGlyph5x7 = 5'b00100;
            3'd5: debugGlyph5x7 = 5'b00100;
            default: debugGlyph5x7 = 5'b00100;
          endcase
        end
        default: debugGlyph5x7 = 5'b00000;
      endcase
    end
  endfunction

  wire [2:0] debugCol = io_video_pos_x[7:5];
  wire [2:0] debugRow = io_video_pos_y[7:5];
  wire [5:0] debugIndex = {debugRow, debugCol};
  wire       debugPanel = ~io_video_pos_x[8] & ~io_video_pos_y[8];
  wire       debugGrid =
    (io_video_pos_x[4:0] == 5'd0) | (io_video_pos_y[4:0] == 5'd0);
  wire       debugCellOn = debugPanel & io_debug_bits[debugIndex];
  wire [23:0] debugOffRgb =
    (io_video_pos_x[3] ^ io_video_pos_y[3]) ? 24'h180818 : 24'h040006;

  reg [23:0] debugRowRgb;

  always @* begin
    case (debugRow)
      3'd0: debugRowRgb = 24'hffffff;
      3'd1: debugRowRgb = 24'h00ff00;
      3'd2: debugRowRgb = 24'hffff00;
      3'd3: debugRowRgb = 24'h00ffff;
      3'd4: debugRowRgb = 24'hff80ff;
      3'd5: debugRowRgb = 24'hff8000;
      3'd6: debugRowRgb = 24'h80a0ff;
      default: debugRowRgb = 24'hff4040;
    endcase
  end

  wire [2:0] debugTextCol = io_video_pos_x[7:5];
  wire [2:0] debugGlyphX = io_video_pos_x[4:2];
  reg  [2:0] debugTextRow;
  reg  [4:0] debugTextRowY;

  always @* begin
    if (io_video_pos_y < 9'd30) begin
      debugTextRow = 3'd0;
      debugTextRowY = io_video_pos_y[4:0];
    end
    else if (io_video_pos_y < 9'd60) begin
      debugTextRow = 3'd1;
      debugTextRowY = io_video_pos_y - 9'd30;
    end
    else if (io_video_pos_y < 9'd90) begin
      debugTextRow = 3'd2;
      debugTextRowY = io_video_pos_y - 9'd60;
    end
    else if (io_video_pos_y < 9'd120) begin
      debugTextRow = 3'd3;
      debugTextRowY = io_video_pos_y - 9'd90;
    end
    else if (io_video_pos_y < 9'd150) begin
      debugTextRow = 3'd4;
      debugTextRowY = io_video_pos_y - 9'd120;
    end
    else if (io_video_pos_y < 9'd180) begin
      debugTextRow = 3'd5;
      debugTextRowY = io_video_pos_y - 9'd150;
    end
    else if (io_video_pos_y < 9'd210) begin
      debugTextRow = 3'd6;
      debugTextRowY = io_video_pos_y - 9'd180;
    end
    else begin
      debugTextRow = 3'd7;
      debugTextRowY = io_video_pos_y - 9'd210;
    end
  end

  wire [2:0] debugGlyphY = debugTextRowY[4:2];
  wire [7:0] debugSoundByte =
    debugTextRow == 3'd0 ? io_debug_bits[7:0] :
    debugTextRow == 3'd1 ? io_debug_bits[15:8] :
    debugTextRow == 3'd2 ? io_debug_bits[23:16] :
    debugTextRow == 3'd3 ? io_debug_bits[31:24] :
    debugTextRow == 3'd4 ? io_debug_bits[39:32] :
    debugTextRow == 3'd5 ? io_debug_bits[47:40] :
    debugTextRow == 3'd6 ? io_debug_bits[55:48] :
                           io_debug_bits[63:56];
  reg [5:0] debugSoundChar;

  always @* begin
    debugSoundChar = DEBUG_CH_SPACE;

    case (debugTextCol)
      3'd0: begin
        case (debugTextRow)
          3'd0: debugSoundChar = 6'd12;         // C
          3'd1: debugSoundChar = DEBUG_CH_R;
          3'd2: debugSoundChar = 6'd11;         // B
          3'd3: debugSoundChar = DEBUG_CH_P;
          3'd4: debugSoundChar = DEBUG_CH_S;
          default: debugSoundChar = DEBUG_CH_H;
        endcase
      end
      3'd1: begin
        case (debugTextRow)
          3'd0: debugSoundChar = DEBUG_CH_M;
          3'd1: debugSoundChar = DEBUG_CH_P;
          3'd2: debugSoundChar = DEBUG_CH_N;
          3'd3: debugSoundChar = DEBUG_CH_H;
          3'd4: debugSoundChar = DEBUG_CH_T;
          3'd5: debugSoundChar = 6'd0;
          3'd6: debugSoundChar = 6'd1;
          default: debugSoundChar = 6'd2;
        endcase
      end
      3'd2: begin
        case (debugTextRow)
          3'd0: debugSoundChar = 6'd13;         // D
          3'd1: debugSoundChar = DEBUG_CH_L;
          3'd2: debugSoundChar = DEBUG_CH_K;
          3'd3: debugSoundChar = DEBUG_CH_R;
          3'd4: debugSoundChar = 6'd10;         // A
          default: debugSoundChar = DEBUG_CH_SPACE;
        endcase
      end
      3'd4: debugSoundChar = {2'b00, debugSoundByte[7:4]};
      3'd5: debugSoundChar = {2'b00, debugSoundByte[3:0]};
      default: debugSoundChar = DEBUG_CH_SPACE;
    endcase
  end

  wire [4:0] debugGlyphRowBits = debugGlyph5x7(debugSoundChar, debugGlyphY);
  reg        debugGlyphPixel;

  always @* begin
    case (debugGlyphX)
      3'd0: debugGlyphPixel = debugGlyphRowBits[4];
      3'd1: debugGlyphPixel = debugGlyphRowBits[3];
      3'd2: debugGlyphPixel = debugGlyphRowBits[2];
      3'd3: debugGlyphPixel = debugGlyphRowBits[1];
      3'd4: debugGlyphPixel = debugGlyphRowBits[0];
      default: debugGlyphPixel = 1'b0;
    endcase
  end

  wire       debugTextGlyphOn =
    debugPanel &
    (io_debug_view == 3'd7) &
    (debugTextCol < 3'd6) &
    (io_video_pos_x[4:0] < 5'd20) &
    (io_video_pos_y[4:0] < 5'd28) &
    (debugGlyphY < 3'd7) &
    (debugGlyphX < 3'd5) &
    debugGlyphPixel;
  wire [23:0] debugTextBg =
    (io_video_pos_x[4] ^ io_video_pos_y[4]) ? 24'h001820 : 24'h000408;

  assign io_rgb =
    !debugPanel ? 24'h000010 :
    io_debug_view == 3'd7 ? (debugTextGlyphOn ? 24'hf8f8d8 : debugTextBg) :
    debugGrid    ? 24'h202020 :
    debugCellOn  ? debugRowRgb :
                   debugOffRgb;
endmodule
