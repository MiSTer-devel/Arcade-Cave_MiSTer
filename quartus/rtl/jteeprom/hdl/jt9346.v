/*  This file is part of JTEEPROM.
    JTEEPROM program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    JTEEPROM program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with JTEEPROM.  If not, see <http://www.gnu.org/licenses/>.

    Author: Jose Tejada Gomez. Twitter: @topapate
    Version: 1.0
    Date: 21-3-2020 */
    
`timescale 1ns/1ps

// Module compatible with Microchip 96C06/46

module jt9346(
    input      clk,        // system clock
    input      rst,        // system reset
    // chip interface
    input      sclk,       // serial clock
    input      sdi,         // serial data in
    output reg sdo,         // serial data out and ready/not busy signal
    input      scs          // chip select, active high. Goes low in between instructions
);

parameter SIZE = 64;

reg  [15:0] mem[0:SIZE-1];
reg         erase_en, write_all;
reg         last_sclk;
wire        sclk_posedge = sclk && !last_sclk;
reg  [ 1:0] op;
reg  [ 5:0] addr;
reg  [15:0] rx_cnt;
reg  [15:0] newdata;
reg  [15:0] dout;
wire [ 7:0] full_op = { op, addr };
reg  [ 5:0] st;
reg  [ 5:0] cnt;

localparam IDLE=6'd1, RX=6'd2, READ=6'd4, WRITE=6'd8, WRITE_ALL=6'h10, PRE_READ=6'h20;

always @(posedge clk) last_sclk <= sclk;
/*
`ifdef JT9346_SIMULATION
    `define REPORT_WRITE (a,v) $display("EEPROM: %X written to %X", v, a);
    `define REPORT_READ  (a,v) $display("EEPROM: %X read from  %X", v, a);
    `define REPORT_ERASE (a  ) $display("EEPROM: %X ERASED", a);
    `define REPORT_ERASEEN  $display("EEPROM: erase enabled");
    `define REPORT_ERASEDIS $display("EEPROM: erase disabled");
    `define REPORT_ERASEALL $display("EEPROM: erase all");
    `define REPORT_WRITEALL $display("EEPROM: write all");
`else
    `define REPORT_WRITE (a,v)
    `define REPORT_READ  (a,v)
    `define REPORT_ERASE (a)
    `define REPORT_ERASEEN
    `define REPORT_ERASEDIS
    `define REPORT_ERASEALL
    `define REPORT_WRITEALL
`endif
*/
always @(posedge clk, posedge rst) begin
    if( rst ) begin
        erase_en <= 1'b0;
        cnt      <= 0;
        newdata  <= 16'hffff;
        st       <= WRITE_ALL;
        sdo      <= 1'b0;
    end else begin
        case( st )
            default: begin
                sdo <= 1; // ready
                if( sclk_posedge && scs && sdi ) begin
                    st <= RX;
                    rx_cnt <= 16'hff80;
                end
            end
            RX: if( sclk_posedge && scs ) begin
                rx_cnt <= { rx_cnt[15], rx_cnt[15:1] };
                { op, addr } <= { full_op[6:0], sdi };
                if( rx_cnt[0] ) begin
                    case( full_op[6:5] ) // op is in bits 6:5
                        2'b10: begin
                            st     <= READ;
                            sdo    <= 0;
                            //`REPORT_READ ( {addr[4:0], sdi}, mem[ {addr[4:0], sdi} ] )
                            dout   <= mem[ {addr[4:0], sdi} ];
                            rx_cnt <= 16'hFFFF;
                        end
                        2'b01: begin
                            st        <= WRITE;
                            rx_cnt    <= 16'h8000;
                            write_all <= 1'b0;
                        end
                        2'b11: begin // ERASE
                            //`REPORT_ERASE({addr[4:0],sdi});
                            mem[ {addr[4:0],sdi} ] <= 16'hffff;
                            st <= IDLE;
                        end
                        2'b00: 
                            case( full_op[4:3] )
                                2'b11: begin
                                    //`REPORT_ERASEEN
                                    erase_en <= 1'b1;
                                    st <= IDLE;
                                end
                                2'b00: begin
                                    //`REPORT_ERASEDIS
                                    erase_en <= 1'b0;
                                    st <= IDLE;
                                end
                                2'b10: begin
                                    if( erase_en ) begin
                                        //`REPORT_ERASEALL
                                        sdo     <= 0; // busy
                                        cnt     <= 0;
                                        newdata <= 16'hffff;
                                        st      <= WRITE_ALL;
                                    end else begin
                                        st <= IDLE;
                                    end
                                end
                                2'b01: begin
                                    //`REPORT_WRITEALL
                                    sdo       <= 0; // busy
                                    st        <= WRITE;
                                    rx_cnt    <= 16'h8000;
                                    write_all <= 1'b1;
                                end
                            endcase
                    endcase
                end
            end else if(!scs) begin
                st <= IDLE;
            end
            WRITE: if( sclk_posedge && scs ) begin
                newdata <= { newdata[14:0], sdi };
                rx_cnt <= { rx_cnt[15], rx_cnt[15:1] };
                sdo    <= 0; // busy
                if( rx_cnt[0] ) begin
                    if( write_all ) begin
                        cnt <= 0;
                        st  <= WRITE_ALL;
                    end else begin
                        //`REPORT_WRITE( addr, { newdata[14:0], sdi } )
                        mem[ addr ] <= { newdata[14:0], sdi };
                        st <= IDLE;
                    end
                end
            end else if(!scs) begin
                st <= IDLE;
            end
            /*
            PRE_READ: if( sclk_posedge && scs ) begin
                st <= READ;
                sdo <= 0;
            end else if(!scs) st<=IDLE;*/
            READ: if( sclk_posedge && scs ) begin
                if(rx_cnt[0]) { sdo, dout} <= { dout, 1'b0 };
                rx_cnt <= rx_cnt>>1;
                if( ~|rx_cnt ) begin
                    st <= IDLE;
                end
            end else if(!scs) begin
                st <= IDLE;
            end
            WRITE_ALL: begin
                mem[cnt] <= newdata;
                cnt <= cnt+6'd1;
                if( cnt == SIZE-1 ) st<=IDLE;
            end
        endcase
    end
end

endmodule
