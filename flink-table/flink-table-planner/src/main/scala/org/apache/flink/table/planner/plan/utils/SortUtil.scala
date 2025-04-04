/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.table.planner.plan.utils

import org.apache.flink.api.common.operators.Order
import org.apache.flink.configuration.ReadableConfig
import org.apache.flink.table.api.TableException
import org.apache.flink.table.planner.calcite.FlinkPlannerImpl
import org.apache.flink.table.planner.codegen.sort.SortCodeGenerator
import org.apache.flink.table.planner.plan.nodes.exec.spec.SortSpec
import org.apache.flink.table.types.logical.RowType

import org.apache.calcite.rel.`type`._
import org.apache.calcite.rel.{RelCollation, RelFieldCollation}
import org.apache.calcite.rel.RelFieldCollation.Direction
import org.apache.calcite.rex.{RexLiteral, RexNode}

import scala.collection.mutable

/** Common methods for Flink sort operators. */
object SortUtil {

  /** Returns limit start value (never null). */
  def getLimitStart(offset: RexNode): Long = if (offset != null) RexLiteral.intValue(offset) else 0L

  /** Returns limit end value (never null). */
  def getLimitEnd(offset: RexNode, fetch: RexNode): Long = {
    if (fetch != null) {
      getLimitStart(offset) + RexLiteral.intValue(fetch)
    } else {
      Long.MaxValue
    }
  }

  /**
   * Returns the direction of the first sort field.
   *
   * @param collationSort
   *   The list of sort collations.
   * @return
   *   The direction of the first sort field.
   */
  def getFirstSortDirection(collationSort: RelCollation): Direction = {
    collationSort.getFieldCollations.get(0).direction
  }

  /**
   * Returns the first sort field.
   *
   * @param collationSort
   *   The list of sort collations.
   * @param rowType
   *   The row type of the input.
   * @return
   *   The first sort field.
   */
  def getFirstSortField(collationSort: RelCollation, rowType: RelDataType): RelDataTypeField = {
    val idx = collationSort.getFieldCollations.get(0).getFieldIndex
    rowType.getFieldList.get(idx)
  }

  /** Returns the default null direction if not specified. */
  def getNullDefaultOrders(ascendings: Array[Boolean]): Array[Boolean] = {
    ascendings.map(asc => FlinkPlannerImpl.defaultNullCollation.last(!asc))
  }

  /** Returns the default null direction if not specified. */
  def getNullDefaultOrder(ascending: Boolean): Boolean = {
    FlinkPlannerImpl.defaultNullCollation.last(!ascending)
  }

  def getSortSpec(fieldCollations: Seq[RelFieldCollation]): SortSpec = {
    val fieldMappingDirections =
      fieldCollations.map(c => (c.getFieldIndex, directionToOrder(c.getDirection)))
    val keys = fieldMappingDirections.map(_._1)
    val orders = fieldMappingDirections.map(_._2 == Order.ASCENDING)
    val nullsIsLast = fieldCollations
      .map(_.nullDirection)
      .map {
        case RelFieldCollation.NullDirection.LAST => true
        case RelFieldCollation.NullDirection.FIRST => false
        case RelFieldCollation.NullDirection.UNSPECIFIED =>
          throw new TableException(s"Do not support UNSPECIFIED for null order.")
      }
      .toArray

    deduplicateSortKeys(keys.toArray, orders.toArray, nullsIsLast)
  }

  def getAscendingSortSpec(fields: Array[Int]): SortSpec = {
    val originalOrders = fields.map(_ => true)
    val nullsIsLast = SortUtil.getNullDefaultOrders(originalOrders)
    deduplicateSortKeys(fields, originalOrders, nullsIsLast)
  }

  private def deduplicateSortKeys(
      keys: Array[Int],
      orders: Array[Boolean],
      nullsIsLast: Array[Boolean]): SortSpec = {
    val builder = SortSpec.builder()
    val keySet = new mutable.HashSet[Int]
    for (i <- keys.indices) {
      if (keySet.add(keys(i))) {
        builder.addField(keys(i), orders(i), nullsIsLast(i))
      }
    }
    builder.build()
  }

  def newSortGen(
      config: ReadableConfig,
      classLoader: ClassLoader,
      originalKeys: Array[Int],
      inputType: RowType): SortCodeGenerator = {
    val sortSpec = SortUtil.getAscendingSortSpec(originalKeys)
    new SortCodeGenerator(config, classLoader, inputType, sortSpec)
  }

  def directionToOrder(direction: Direction): Order = {
    direction match {
      case Direction.ASCENDING | Direction.STRICTLY_ASCENDING => Order.ASCENDING
      case Direction.DESCENDING | Direction.STRICTLY_DESCENDING => Order.DESCENDING
      case _ => throw new IllegalArgumentException("Unsupported direction.")
    }
  }
}
