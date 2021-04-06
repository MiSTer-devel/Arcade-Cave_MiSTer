// 
// sd_card.v
//
// Copyright (c) 2014 Till Harbaum <till@harbaum.org>
// Copyright (c) 2015-2018 Sorgelig
//
// This source file is free software: you can redistribute it and/or modify
// it under the terms of the Lesser GNU General Public License as published
// by the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This source file is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// http://elm-chan.org/docs/mmc/mmc_e.html
//
/////////////////////////////////////////////////////////////////////////

//
// Made module syncrhronous. Total code refactoring. (Sorgelig)
// clk_spi must be at least 2 x sck for proper work.

module sd_card #(parameter WIDE = 0, OCTAL=0)
(
	input             clk_sys,
	input             reset,

	input             sdhc,
	input             img_mounted,
	input      [63:0] img_size,

	output reg [31:0] sd_lba,
	output reg        sd_rd,
	output reg        sd_wr,
	input             sd_ack,

	input      [AW:0] sd_buff_addr,
	input      [DW:0] sd_buff_dout,
	output     [DW:0] sd_buff_din,
	input             sd_buff_wr,

	// SPI interface
	input             clk_spi,

	input             ss,
	input             sck,
	input      [SW:0] mosi,
	output reg [SW:0] miso
);

localparam AW = WIDE ?  7 : 8;
localparam DW = WIDE ? 15 : 7;
localparam SW = OCTAL ? 7 : 0;

wire[31:0] OCR = { 1'b1, csd_sdhc, 30'd0 };  // bit30 = 1 -> high capaciry card (sdhc) // bit31 = 0 -> card power up finished
wire [7:0] READ_DATA_TOKEN     = 8'hfe;
wire [7:0] WRITE_DATA_RESPONSE = 8'h05;

// number of bytes to wait after a command before sending the reply
localparam NCR=3;

localparam RD_STATE_IDLE       = 0;
localparam RD_STATE_WAIT_IO    = 1;
localparam RD_STATE_SEND_TOKEN = 2;
localparam RD_STATE_SEND_DATA  = 3;
localparam RD_STATE_WAIT_M     = 4;

localparam WR_STATE_IDLE       = 0;
localparam WR_STATE_EXP_DTOKEN = 1;
localparam WR_STATE_RECV_DATA  = 2;
localparam WR_STATE_RECV_CRC0  = 3;
localparam WR_STATE_RECV_CRC1  = 4;
localparam WR_STATE_SEND_DRESP = 5;
localparam WR_STATE_BUSY       = 6;

localparam PREF_STATE_IDLE     = 0;
localparam PREF_STATE_RD       = 1;
localparam PREF_STATE_FINISH   = 2;

altsyncram sdbuf
(
	.clock0    (clk_sys),
	.address_a ({sd_buf,sd_buff_addr}),
	.data_a    (sd_buff_dout),
	.wren_a    (sd_ack & sd_buff_wr),
	.q_a       (sd_buff_din),

	.clock1    (clk_spi),
	.address_b ({spi_buf,buffer_ptr}),
	.data_b    (buffer_din),
	.wren_b    (buffer_wr),
	.q_b       (buffer_dout),

	.aclr0(1'b0),
	.aclr1(1'b0),
	.addressstall_a(1'b0),
	.addressstall_b(1'b0),
	.byteena_a(1'b1),
	.byteena_b(1'b1),
	.clocken0(1'b1),
	.clocken1(1'b1),
	.clocken2(1'b1),
	.clocken3(1'b1),
	.eccstatus(),
	.rden_a(1'b1),
	.rden_b(1'b1)
);
defparam
	sdbuf.numwords_a = 1<<(AW+3),
	sdbuf.widthad_a  = AW+3,
	sdbuf.width_a    = DW+1,
	sdbuf.numwords_b = 2048,
	sdbuf.widthad_b  = 11,
	sdbuf.width_b    = 8,
	sdbuf.address_reg_b = "CLOCK1",
	sdbuf.clock_enable_input_a = "BYPASS",
	sdbuf.clock_enable_input_b = "BYPASS",
	sdbuf.clock_enable_output_a = "BYPASS",
	sdbuf.clock_enable_output_b = "BYPASS",
	sdbuf.indata_reg_b = "CLOCK1",
	sdbuf.intended_device_family = "Cyclone V",
	sdbuf.lpm_type = "altsyncram",
	sdbuf.operation_mode = "BIDIR_DUAL_PORT",
	sdbuf.outdata_aclr_a = "NONE",
	sdbuf.outdata_aclr_b = "NONE",
	sdbuf.outdata_reg_a = "UNREGISTERED",
	sdbuf.outdata_reg_b = "UNREGISTERED",
	sdbuf.power_up_uninitialized = "FALSE",
	sdbuf.read_during_write_mode_port_a = "NEW_DATA_NO_NBE_READ",
	sdbuf.read_during_write_mode_port_b = "NEW_DATA_NO_NBE_READ",
	sdbuf.width_byteena_a = 1,
	sdbuf.width_byteena_b = 1,
	sdbuf.wrcontrol_wraddress_reg_b = "CLOCK1";

reg [26:0] csd_size;
reg        csd_sdhc;
always @(posedge clk_sys) begin
	if (img_mounted) begin
		csd_sdhc <= sdhc;
		if (sdhc) begin
			csd_size[0]     <= 0;
			csd_size[22:1]  <= img_size[40:19]; // in 512K units
			csd_size[26:23] <= 0;
		end
		else begin
			csd_size[2:0]   <= 7; // C_SIZE_MULT
			csd_size[14:3]  <= 12'b101101101101;
			csd_size[26:15] <= img_size[29:18]; // in 256K units ((2**(C_SIZE_MULT+2))*512)
		end
	end
end

wire [127:0] CSD = {1'b0,csd_sdhc,6'h00,8'h0e,8'h00,8'h32,8'h5b,8'h59,6'h00,csd_size,7'h7f,8'h80,8'h0a,8'h40,8'h40,8'hf1};
wire [127:0] CID = {8'hcd,8'hc7,8'h00,8'h93,8'h6f,8'h2f,8'h73,8'h00,8'h00,8'h44,8'h32,8'h38,8'h34,8'h00,8'h00,8'h3e};

reg [31:0] new_lba;
reg  [8:0] buffer_ptr;
reg  [7:0] buffer_din;
wire [7:0] buffer_dout;
wire [7:0] config_dout;
reg        buffer_wr;

reg  [1:0] sd_buf, spi_buf;

reg  [6:0] sbuf;
wire [7:0] ibuf = OCTAL ? mosi : {sbuf,mosi[0]};

always @(posedge clk_spi) begin
	reg [2:0] read_state;
	reg [2:0] write_state;
	reg       cmd55;
	reg [5:0] cmd;
	reg [2:0] bit_cnt;
	reg [3:0] byte_cnt;
	reg [7:0] reply;
	reg [7:0] reply0, reply1, reply2, reply3;
	reg [3:0] reply_len;
	reg       tx_finish;
	reg       rx_finish;
	reg       old_sck;
	reg       synced;
	reg [5:0] ack;
	reg [4:0] idle_cnt = 0;
	reg [2:0] wait_m_cnt;
	reg [1:0] pref_state;

	if(buffer_wr & ~&buffer_ptr) buffer_ptr <= buffer_ptr + 1'd1;
	buffer_wr <= 0;

	ack <= {ack[4:0], sd_ack};
	if(ack[5:4] == 2'b01) {sd_rd,sd_wr} <= 0;
	if(ack[5:4] == 2'b10) begin
		sd_buf <= sd_buf + 1'd1;
		sd_lba <= sd_lba + 1;
	end
	
	case(pref_state)
		PREF_STATE_IDLE:
			if(((sd_buf - spi_buf) < 2) && (read_state != RD_STATE_IDLE)) begin
				sd_rd <= 1;
				pref_state <= PREF_STATE_RD;
			end

		PREF_STATE_RD:
			if(read_state == RD_STATE_IDLE) begin
				pref_state <= PREF_STATE_IDLE;
			end
			else if(ack[5:4] == 2'b10) begin
				pref_state <= (cmd == 18) ? PREF_STATE_IDLE : PREF_STATE_FINISH;
			end

		PREF_STATE_FINISH:
			if(read_state == RD_STATE_IDLE) begin
				pref_state <= PREF_STATE_IDLE;
			end
	endcase

	old_sck <= sck;
	
	if(~ss)                              idle_cnt <= 31;
	else if(~old_sck && sck && idle_cnt) idle_cnt <= idle_cnt - 1'd1;

	if(reset || (OCTAL ? ss : !idle_cnt)) begin
		bit_cnt     <= OCTAL ? 3'd7 : 3'd0;
		byte_cnt    <= 15;
		miso        <= '1;
		tx_finish   <= 0;
		rx_finish   <= 0;
		cmd         <= 0;
		read_state  <= RD_STATE_IDLE;
		write_state <= WR_STATE_IDLE;
		pref_state  <= PREF_STATE_IDLE;
	end

	if(old_sck & ~sck & ~ss) begin
		tx_finish <= 0;
		miso <= '1;				// default: send 1's (busy/wait)

		if(byte_cnt == 5+NCR) begin
			miso <= OCTAL ? reply[SW:0] : reply[~bit_cnt];

			if(bit_cnt == 7) begin
				// these three commands all have a reply_len of 0 and will thus
				// not send more than a single reply byte

				// CMD9: SEND_CSD
				// CMD10: SEND_CID
				if(cmd == 9 || cmd == 10)
					read_state <= RD_STATE_SEND_TOKEN;      // jump directly to data transmission
						
				// CMD17/CMD18
				if(cmd == 17 || cmd == 18) begin
					spi_buf <= 0;
					sd_buf  <= 0;
					sd_lba  <= csd_sdhc ? new_lba : {9'd0, new_lba[31:9]};
					read_state <= RD_STATE_WAIT_IO;         // start waiting for data from io controller
				end
			end
		end
		else if((reply_len > 0) && (byte_cnt == 5+NCR+1)) miso <= OCTAL ? reply0[SW:0] : reply0[~bit_cnt];
		else if((reply_len > 1) && (byte_cnt == 5+NCR+2)) miso <= OCTAL ? reply1[SW:0] : reply1[~bit_cnt];
		else if((reply_len > 2) && (byte_cnt == 5+NCR+3)) miso <= OCTAL ? reply2[SW:0] : reply2[~bit_cnt];
		else if((reply_len > 3) && (byte_cnt == 5+NCR+4)) miso <= OCTAL ? reply3[SW:0] : reply3[~bit_cnt];
		else begin
			if(byte_cnt > 5+NCR && read_state==RD_STATE_IDLE && write_state==WR_STATE_IDLE) tx_finish <= 1;
		end

		// ---------- read state machine processing -------------

		case(read_state)
			RD_STATE_IDLE: ; // do nothing


			// waiting for io controller to return data
			RD_STATE_WAIT_IO: begin
				if(sd_buf != spi_buf && bit_cnt == 7) read_state <= RD_STATE_SEND_TOKEN;
			end

			// send data token
			RD_STATE_SEND_TOKEN: begin
				miso <= OCTAL ? READ_DATA_TOKEN[SW:0] : READ_DATA_TOKEN[~bit_cnt];
	
				if(bit_cnt == 7) begin
					read_state <= RD_STATE_SEND_DATA;   // next: send data
					buffer_ptr <= 0;
				end
			end

			// send data
			RD_STATE_SEND_DATA: begin

				if(OCTAL) miso <= (cmd == 9) ? CSD[{buffer_ptr[3:0],3'd0} +:8] : (cmd == 10) ? CID[{buffer_ptr[3:0],3'd0} +:8] : buffer_dout;
				else      miso <= (cmd == 9) ? CSD[{buffer_ptr[3:0],~bit_cnt}] : (cmd == 10) ? CID[{buffer_ptr[3:0],~bit_cnt}] : buffer_dout[~bit_cnt];

				if(bit_cnt == 7) begin

					// sent 512 sector data bytes?
					if(cmd == 17 && &buffer_ptr) read_state <= RD_STATE_IDLE;
					else if(cmd == 18 && &buffer_ptr) begin
						read_state <= RD_STATE_WAIT_M;
						wait_m_cnt <= 0;
					end

					// sent 16 cid/csd data bytes?
					else if((cmd == 9 || cmd == 10) && &buffer_ptr[3:0]) read_state <= RD_STATE_IDLE;

					// not done yet -> trigger read of next data byte
					else buffer_ptr <= buffer_ptr + 1'd1;
				end
			end

			RD_STATE_WAIT_M: begin
				if(bit_cnt == 7) begin
					wait_m_cnt <= wait_m_cnt + 1'd1;
					if(&wait_m_cnt) begin
						spi_buf <= spi_buf + 1'd1;
						read_state <= RD_STATE_WAIT_IO;
					end
				end
			end
		endcase

		// ------------------ write support ----------------------
		// send write data response
		if(write_state == WR_STATE_SEND_DRESP) miso <= OCTAL ? WRITE_DATA_RESPONSE[SW:0] : WRITE_DATA_RESPONSE[~bit_cnt];

		// busy after write until the io controller sends ack
		if(write_state == WR_STATE_BUSY) miso <= 0;
   end

	if(~old_sck & sck & ~ss) begin
		if(bit_cnt != 7 && !OCTAL) begin
			// resync while waiting for token
			if(write_state == WR_STATE_EXP_DTOKEN) begin
				if(cmd == 24) begin
					if(ibuf == 8'hfe) begin
						write_state <= WR_STATE_RECV_DATA;
						buffer_ptr <= 0;
						bit_cnt <= 0;
					end
				end
				else begin
					if(ibuf == 8'hfc) begin
						write_state <= WR_STATE_RECV_DATA;
						buffer_ptr <= 0;
						bit_cnt <= 0;
					end
					if(ibuf == 8'hfd) begin
						write_state <= WR_STATE_IDLE;
						rx_finish <= 1;
						bit_cnt <= 0;
					end
				end
			end
		end
		else begin
			// finished reading one byte
			// byte counter runs against 15 byte boundary
			if(byte_cnt != 15) byte_cnt <= byte_cnt + 1'd1;

			// byte_cnt > 6 -> complete command received
			// first byte of valid command is 01xxxxxx
			// don't accept new commands once a write or read command has been accepted
 			if(byte_cnt > 5 && write_state == WR_STATE_IDLE && read_state == RD_STATE_IDLE && (OCTAL || !rx_finish) && ibuf[7:6] == 1) begin
				byte_cnt <= 0;
				cmd      <= ibuf[5:0];
				cmd55    <= (cmd == 55); // set cmd55 flag if previous command was 55
			end

 			if(byte_cnt > 5 && read_state != RD_STATE_IDLE && ibuf == 8'h4c) begin
				byte_cnt   <= 0;
				rx_finish  <= 0;
				cmd        <= 12;
				read_state <= RD_STATE_IDLE;
			end

			// parse additional command bytes
			if(byte_cnt == 0) new_lba[31:24] <= ibuf;
			if(byte_cnt == 1) new_lba[23:16] <= ibuf;
			if(byte_cnt == 2) new_lba[15:8]  <= ibuf;
			if(byte_cnt == 3) new_lba[7:0]   <= ibuf;

			// last byte (crc) received, evaluate
			if(byte_cnt == 4) begin

				// default:
				reply <= 4;      // illegal command
				reply_len <= 0;  // no extra reply bytes
				rx_finish <= 1;

				case(cmd)
					// CMD0: GO_IDLE_STATE
					 0: reply <= 1;    // ok, busy

					// CMD1: SEND_OP_COND
					 1: reply <= 0;    // ok, not busy

					// CMD8: SEND_IF_COND (V2 only)
					 8: begin
							reply  <= 1;   // ok, busy

							reply0 <= 'h00;
							reply1 <= 'h00;
							reply2 <= 'h01;
							reply3 <= 'hAA;
							reply_len <= 4;
						end

					// CMD9: SEND_CSD
					 9: reply <= 0;    // ok

					// CMD10: SEND_CID
					10: reply <= 0;    // ok

					// CMD12: STOP_TRANSMISSION 
					12: reply <= 0;    // ok
					
					// CMD13: SEND_STATUS 
					13: begin
					        reply <= 'h00;    // ok 
							  reply0 <='h00;
							  reply_len <= 1;
					      end

					// CMD16: SET_BLOCKLEN
					16: begin
							// we only support a block size of 512
							if(new_lba == 512) reply <= 0;  // ok
								else reply <= 'h40;         // parmeter error
						end

					// CMD17: READ_SINGLE_BLOCK
					17: reply <= 0;    // ok

					// CMD18: READ_MULTIPLE
					18: reply <= 0;    // ok

					// ACMD23:  SET_WR_BLK_ERASE_COUNT
					23: reply <= 0;     //ok

					// CMD24: WRITE_BLOCK
					24,
					// CMD25: WRITE_MULTIPLE
					25: begin
							reply <= 0;    // ok
							write_state <= WR_STATE_EXP_DTOKEN;  // expect data token
							rx_finish <=0;
							sd_lba <= csd_sdhc ? new_lba : {9'd0, new_lba[31:9]};
							spi_buf <= 0;
							sd_buf <= 0;
						end

					// ACMD41: APP_SEND_OP_COND
					41: if(cmd55) reply <= 0;    // ok, not busy

					// CMD55: APP_COND
					55: reply <= 1;    // ok, busy

					// CMD58: READ_OCR
					58: begin
							reply <= 0;    // ok

							reply0 <= OCR[31:24];   // bit 30 = 1 -> high capacity card
							reply1 <= OCR[23:16];
							reply2 <= OCR[15:8];
							reply3 <= OCR[7:0];
							reply_len <= 4;
						end

					// CMD59: CRC_ON_OFF
					59: reply <= 0;    // ok
				endcase
			end

				// ---------- handle write -----------
			case(write_state)
				// do nothing in idle state
				WR_STATE_IDLE: ;

				// waiting for data token
				WR_STATE_EXP_DTOKEN: begin
					buffer_ptr <= 0;
					if(cmd == 24) begin
						if(ibuf == 8'hfe) write_state <= WR_STATE_RECV_DATA;
					end
					else begin
						if(ibuf == 8'hfc) write_state <= WR_STATE_RECV_DATA;
						if(ibuf == 8'hfd) begin
							write_state <= WR_STATE_IDLE;
							rx_finish <= 1;
						end
					end
				end

				// transfer 512 bytes
				WR_STATE_RECV_DATA: begin
					// push one byte into local buffer
					buffer_wr  <= 1;
					buffer_din <= ibuf;

					// all bytes written?
					if(&buffer_ptr) write_state <= WR_STATE_RECV_CRC0;
				end

				// transfer 1st crc byte
				WR_STATE_RECV_CRC0:
					write_state <= WR_STATE_RECV_CRC1;

				// transfer 2nd crc byte
				WR_STATE_RECV_CRC1:
					write_state <= WR_STATE_SEND_DRESP;

				// send data response
				WR_STATE_SEND_DRESP: begin
					write_state <= WR_STATE_BUSY;
					spi_buf <= spi_buf + 1'd1;
					sd_wr <= 1;
				end

				// wait for io controller to accept data
				WR_STATE_BUSY:
					if(spi_buf == sd_buf) begin
						if(cmd == 25) begin
							write_state <= WR_STATE_EXP_DTOKEN;
							sd_lba <= sd_lba + 1;
						end
						else begin
							write_state <= WR_STATE_IDLE;
							rx_finish <= 1;
						end
					end
			endcase
		end

		if(!OCTAL) begin
			if(reset || !idle_cnt) begin
				synced <= 0;
				sbuf   <= 7'b1111111;
			end

			// wait for first 0 bit until start counting bits
			if(~synced && sbuf == 7'b1111101) begin
				synced   <= 1;
				bit_cnt  <= 3;
				rx_finish<= 0;
			end
			else if(synced) begin
				bit_cnt <= bit_cnt + 1'd1;
				if(tx_finish && rx_finish ) begin
					synced   <= 0;
					bit_cnt  <= 0;
					rx_finish<= 0;
				end
			end

			sbuf[6:0] <= {sbuf[5:0],mosi};
		end
	end
end

endmodule
