/*
 * Copyright 2020 MariaDB Ab.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mariadb.r2dbc.codec.list;

import io.netty.buffer.ByteBuf;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import org.mariadb.r2dbc.client.Context;
import org.mariadb.r2dbc.codec.Codec;
import org.mariadb.r2dbc.codec.DataType;
import org.mariadb.r2dbc.message.server.ColumnDefinitionPacket;
import org.mariadb.r2dbc.util.BufferUtils;

public class ByteCodec implements Codec<Byte> {

  public static final ByteCodec INSTANCE = new ByteCodec();

  private static final EnumSet<DataType> COMPATIBLE_TYPES =
      EnumSet.of(
          DataType.TINYINT,
          DataType.SMALLINT,
          DataType.MEDIUMINT,
          DataType.INTEGER,
          DataType.BIGINT,
          DataType.YEAR,
          DataType.BIT,
          DataType.FLOAT,
          DataType.DOUBLE,
          DataType.OLDDECIMAL,
          DataType.BLOB,
          DataType.TINYBLOB,
          DataType.MEDIUMBLOB,
          DataType.LONGBLOB,
          DataType.DECIMAL,
          DataType.ENUM,
          DataType.VARSTRING,
          DataType.STRING,
          DataType.VARCHAR);

  public static long parseBit(ByteBuf buf, int length) {
    if (length == 1) {
      return buf.readUnsignedByte();
    }
    long val = 0;
    int idx = 0;
    do {
      val += ((long) buf.readUnsignedByte()) << (8 * length);
      idx++;
    } while (idx < length);
    return val;
  }

  public String className() {
    return Byte.class.getName();
  }

  public boolean canDecode(ColumnDefinitionPacket column, Class<?> type) {
    return COMPATIBLE_TYPES.contains(column.getType())
        && ((type.isPrimitive() && type == Byte.TYPE) || type.isAssignableFrom(Byte.class));
  }

  public boolean canEncode(Object value) {
    return value instanceof Byte;
  }

  @Override
  public Byte decodeText(
      ByteBuf buf, int length, ColumnDefinitionPacket column, Class<? extends Byte> type) {

    long result;
    switch (column.getType()) {
      case TINYINT:
      case SMALLINT:
      case MEDIUMINT:
      case INTEGER:
      case BIGINT:
      case YEAR:
        result = LongCodec.parse(buf, length);
        break;

      case BIT:
        Byte val = buf.readByte();
        if (length > 1) buf.skipBytes(length - 1);
        return val;

      case FLOAT:
      case DOUBLE:
      case OLDDECIMAL:
      case DECIMAL:
      case ENUM:
      case VARCHAR:
      case VARSTRING:
      case STRING:
        String str = buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
        try {
          result = new BigDecimal(str).setScale(0, RoundingMode.DOWN).byteValueExact();
        } catch (NumberFormatException | ArithmeticException nfe) {
          throw new R2dbcNonTransientResourceException(
              String.format("value '%s' (%s) cannot be decoded as Byte", str, column.getType()));
        }
        break;

      case BLOB:
      case TINYBLOB:
      case MEDIUMBLOB:
      case LONGBLOB:
        if (length > 0) {
          byte b = buf.readByte();
          buf.skipBytes(length - 1);
          return b;
        }
        throw new R2dbcNonTransientResourceException(
            "empty String value cannot be decoded as Byte");

      default:
        buf.skipBytes(length);
        throw new R2dbcNonTransientResourceException(
            String.format("Data type %s cannot be decoded as Byte", column.getType()));
    }

    if ((byte) result != result || (result < 0 && !column.isSigned())) {
      throw new R2dbcNonTransientResourceException("byte overflow");
    }

    return (byte) result;
  }

  @Override
  public Byte decodeBinary(
      ByteBuf buf, int length, ColumnDefinitionPacket column, Class<? extends Byte> type) {

    long result;
    switch (column.getType()) {
      case TINYINT:
        result = column.isSigned() ? buf.readByte() : buf.readUnsignedByte();
        break;

      case YEAR:
      case SMALLINT:
        result = column.isSigned() ? buf.readShortLE() : buf.readUnsignedShortLE();
        break;

      case MEDIUMINT:
        result = column.isSigned() ? buf.readMediumLE() : buf.readUnsignedMediumLE();
        break;

      case INTEGER:
        result = column.isSigned() ? buf.readIntLE() : buf.readUnsignedIntLE();
        break;

      case BIGINT:
        if (column.isSigned()) {
          result = buf.readLongLE();
        } else {
          // need BIG ENDIAN, so reverse order
          byte[] bb = new byte[8];
          for (int i = 7; i >= 0; i--) {
            bb[i] = buf.readByte();
          }
          BigInteger val = new BigInteger(1, bb);
          result = val.longValue();
        }
        break;

      case BIT:
        Byte val = buf.readByte();
        if (length > 1) buf.skipBytes(length - 1);
        return val;

      case FLOAT:
        float f = buf.readFloatLE();
        result = (long) f;
        if ((byte) result != result || (result < 0 && !column.isSigned())) {
          throw new R2dbcNonTransientResourceException(
              String.format("value '%s' (%s) cannot be decoded as Byte", f, column.getType()));
        }
        break;

      case DOUBLE:
        double d = buf.readDoubleLE();
        result = (long) d;
        if ((byte) result != result || (result < 0 && !column.isSigned())) {
          throw new R2dbcNonTransientResourceException(
              String.format("value '%s' (%s) cannot be decoded as Byte", d, column.getType()));
        }
        break;

      case OLDDECIMAL:
      case DECIMAL:
      case ENUM:
      case VARCHAR:
      case VARSTRING:
      case STRING:
        String str = buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
        try {
          result = new BigDecimal(str).setScale(0, RoundingMode.DOWN).byteValueExact();
        } catch (NumberFormatException | ArithmeticException nfe) {
          throw new R2dbcNonTransientResourceException(
              String.format("value '%s' (%s) cannot be decoded as Byte", str, column.getType()));
        }
        break;

      case BLOB:
      case TINYBLOB:
      case MEDIUMBLOB:
      case LONGBLOB:
        if (length > 0) {
          byte b = buf.readByte();
          buf.skipBytes(length - 1);
          return b;
        }
        throw new R2dbcNonTransientResourceException(
            "empty String value cannot be decoded as Byte");

      default:
        buf.skipBytes(length);
        throw new R2dbcNonTransientResourceException(
            String.format("Data type %s cannot be decoded as Byte", column.getType()));
    }

    if ((byte) result != result || (result < 0 && !column.isSigned())) {
      throw new R2dbcNonTransientResourceException("byte overflow");
    }

    return (byte) result;
  }

  @Override
  public void encodeText(ByteBuf buf, Context context, Byte value) {
    BufferUtils.writeAscii(buf, Integer.toString((int) value));
  }

  @Override
  public void encodeBinary(ByteBuf buf, Context context, Byte value) {
    buf.writeByte(value);
  }

  public DataType getBinaryEncodeType() {
    return DataType.TINYINT;
  }

  @Override
  public String toString() {
    return "ByteCodec{}";
  }
}
