/*
 * Copyright (C) 2015 XiNGRZ <chenxingyu92@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package me.xingrz.prox.udp.dns;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import me.xingrz.prox.internet.IpUtils;
import me.xingrz.prox.logging.FormattingLogger;
import me.xingrz.prox.logging.FormattingLoggers;

public class DnsRecord {

    public String name;
    public Set<Integer> addresses;

    private DnsRecord(String name) {
        this.name = name;
        this.addresses = new HashSet<>();
    }

    private static final FormattingLogger logger = FormattingLoggers.getContextLogger();

    private static final int RR_TYPE_A = 1;
    private static final int RR_TYPE_CNAME = 5;

    private static final int RR_CLASS_IN = 1;

    private static final byte[] buf = new byte[0xFFFF];

    /**
     * 尝试从 UDP 数据包中解析出 DNS 应答
     *
     * @param buffer UDP 数据包中的数据体
     * @return {@link DnsRecord} 或 {@code null}
     * @see <a href="http://tools.ietf.org/html/rfc1035">RFC1035</a>
     * @see <a href="http://www.firewall.cx/networking-topics/protocols/domain-name-system-dns/160-protocols-dns-query.html">DNS 查询消息格式</a>
     * @see <a href="http://www.firewall.cx/networking-topics/protocols/domain-name-system-dns/161-protocols-dns-response.html">DNS 应答消息格式</a>
     */
    public static DnsRecord[] parse(ByteBuffer buffer) {
        try {
            // Transaction ID
            buffer.position(2);

            short flag = buffer.getShort();

            // Response
            if ((flag & 0x8000) != 0x8000) {
                logger.v("Not a query response");
                return null;
            }

            // Standard Query
            if ((flag & 0x7800) != 0x0000) {
                logger.v("Not a query");
                return null;
            }

            // No Error
            /*if ((flag & 0x000F) != 0x0000) {
                logger.v("Has error");
                return null;
            }*/

            short queries = buffer.getShort();
            short answers = buffer.getShort();

            if (queries < 1 || answers < 1) {
                return null;
            }

            // Authority 和 Additional 我们不关心的，跳过
            buffer.position(buffer.position() + 2 + 2);

            HashMap<String, DnsRecord> records = new HashMap<>();
            //HashMap<String, String> canonicals = new HashMap<>();

            // 解析查询部分
            for (short i = 0; i < queries; i++) {
                String name = getNameFrom(buffer);
                short type = buffer.getShort();
                short cls = buffer.getShort();

                logger.v("Query: %s, Type: %d, Class: %d", name, type, cls);

                if (type != RR_TYPE_A) {
                    continue;
                }

                if (cls != RR_CLASS_IN) {
                    continue;
                }

                records.put(name, new DnsRecord(name));
            }

            for (short i = 0; i < answers; i++) {
                String name = getNameFrom(buffer);
                short type = buffer.getShort();
                short cls = buffer.getShort();

                logger.v("Answer: %s, Type: %d, Class: %d", name, type, cls);

                if (cls != RR_CLASS_IN) {
                    continue;
                }

                // TTL 和 data length 不关我事，跳过
                buffer.position(buffer.position() + 4 + 2);

                if (type == RR_TYPE_A) {
                    int ip = buffer.getInt();
                    logger.v("A record: %s", IpUtils.toString(ip));

                    if (records.containsKey(name)) {
                        records.get(name).addresses.add(ip);
                    }
                } else if (type == RR_TYPE_CNAME) {
                    String canonical = getNameFrom(buffer);
                    //canonicals.put(name, canonical);

                    // TODO: 我们现在还没想好怎么处理 CNAME 的情况
                    logger.v("CNAME record: %s -> %s", name, canonical);
                }
            }

            return records.values().toArray(new DnsRecord[records.size()]);
        } catch (RuntimeException e) {
            logger.v("Not a DNS response due to invalid bound");
            return null;
        }
    }

    /**
     * 从 {@code buffer} 的当前位置读出域名，并将其位置移到域名记录之后
     * <p/>
     * DNS 报文中域名的记录方式是，将一个域名（比如 {@code xingrz.github.io}）按 {@code .} 分割成若干段，
     * 每一段前面用一个字节表示这一段的字节数，直到 {@code 0x00} 结束。
     *
     * @param buffer 缓冲区
     * @return 域名
     * @throws RuntimeException 读取超出 {@code buffer} 边界，比如该 {@code buffer} 并非一个有效
     *                          的域名记录
     */
    private static String getNameFrom(ByteBuffer buffer) throws RuntimeException {
        StringBuilder builder = new StringBuilder();

        int end = buffer.position();
        boolean ended = false;

        byte length;
        while ((length = buffer.get()) != 0) {
            // 如果的到的是一个指针，则把 position 移动到那个位置继续读取
            // 同时要记得记录下移动之前的 position 位置
            // 因为读完之后我们要把它移到当前记录之后！
            // 一个指针的头倆 bit 是 11，可以用 0xC0 (11000000) 掩码读出
            // 要得到后面的指针位置，同样需要掩码 0x3FFF (00111111 11111111)
            if ((length & 0xC0) == 0xC0) {
                // 遇到指针，终点计算到此为止
                end = buffer.position();
                ended = true;

                buffer.position(buffer.getShort(buffer.position() - 1) & 0x3FFF);
            } else {
                // 如果这个 label 不是从指针指过来的，则要累加终点偏移值
                if (!ended) {
                    end += 1 + length;
                }

                buffer.get(buf, 0, length);
                builder.append(new String(buf, 0, length)).append('.' );
            }
        }

        // 最后，因为上面的游标已经因为指针被跳来跳去不知道去哪了
        // 所以我们要把游标定回我们计算出来的终点位置
        // 之所以要 +1，是因为上面的 while 循环最后会遇到一个 0x00 字节，需要计入在内
        buffer.position(end + 1);

        return builder.substring(0, builder.length() - 1);
    }

}
