// This file is a Codex-assisted rewrite based on the original work of
// Josh Bassett (nullobject).

// Page address generator for sprite and system frame buffers.
module CavePageFlipper #(
  parameter [10:0] BASE_PAGE = 11'h120,
  parameter        SUPPORT_TRIPLE_BUFFER = 1'b0
) (
  input         clock,
  input         reset,
  input         io_mode,
  input         io_swapRead,
  input         io_swapWrite,
  output [31:0] io_addrRead,
  output [31:0] io_addrWrite
);
  reg [1:0] read_index;
  reg [1:0] write_index;

  function [1:0] next_index;
    input [1:0] current;
    input [1:0] other;
    begin
      if ((current == 2'h0 && other == 2'h1) || (current == 2'h1 && other == 2'h0))
        next_index = 2'h2;
      else if ((current == 2'h1 && other == 2'h2) || (current == 2'h2 && other == 2'h1))
        next_index = 2'h0;
      else
        next_index = 2'h1;
    end
  endfunction

  always @(posedge clock) begin
    if (reset) begin
      read_index <= 2'h0;
      write_index <= 2'h1;
    end
    else if (SUPPORT_TRIPLE_BUFFER && io_mode) begin
      if (io_swapRead && io_swapWrite) begin
        read_index <= write_index;
        write_index <= next_index(write_index, read_index);
      end
      else if (io_swapRead) begin
        read_index <= next_index(read_index, write_index);
      end
      else if (io_swapWrite) begin
        write_index <= next_index(write_index, read_index);
      end
    end
    else if (io_swapWrite) begin
      read_index <= {1'b0, write_index[0]};
      write_index <= {1'b0, ~write_index[0]};
    end
  end

  assign io_addrRead = {BASE_PAGE, read_index, 19'h0};
  assign io_addrWrite = {BASE_PAGE, write_index, 19'h0};
endmodule
