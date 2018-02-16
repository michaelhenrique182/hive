/**
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

package org.apache.hadoop.hive.ql.exec.vector.expressions;

import java.util.Arrays;

import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorExpressionDescriptor;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;

/**
 * This class performs OR expression on two input columns and stores,
 * the boolean output in a separate output column. The boolean values
 * are supposed to be represented as 0/1 in a long vector.
 */
public class ColOrCol extends VectorExpression {

  private static final long serialVersionUID = 1L;

  private int colNum1;
  private int colNum2;
  private int outputColumn;

  public ColOrCol(int colNum1, int colNum2, int outputColumn) {
    this();
    this.colNum1 = colNum1;
    this.colNum2 = colNum2;
    this.outputColumn = outputColumn;
  }

  public ColOrCol() {
    super();
  }

  @Override
  public void evaluate(VectorizedRowBatch batch) {

    if (childExpressions != null) {
      super.evaluateChildren(batch);
    }

    LongColumnVector inputColVector1 = (LongColumnVector) batch.cols[colNum1];
    LongColumnVector inputColVector2 = (LongColumnVector) batch.cols[colNum2];
    int[] sel = batch.selected;
    int n = batch.size;
    long[] vector1 = inputColVector1.vector;
    long[] vector2 = inputColVector2.vector;

    LongColumnVector outV = (LongColumnVector) batch.cols[outputColumn];
    long[] outputVector = outV.vector;
    if (n <= 0) {
      // Nothing to do
      return;
    }

    boolean[] outputIsNull = outV.isNull;

    // We do not need to do a column reset since we are carefully changing the output.
    outV.isRepeating = false;

    long vector1Value = vector1[0];
    long vector2Value = vector2[0];
    if (inputColVector1.noNulls && inputColVector2.noNulls) {
      if ((inputColVector1.isRepeating) && (inputColVector2.isRepeating)) {

        // All must be selected otherwise size would be zero
        // Repeating property will not change.
        outV.isRepeating = true;
        outputIsNull[0] = false;
        outputVector[0] = vector1[0] | vector2[0];
      } else if (inputColVector1.isRepeating && !inputColVector2.isRepeating) {
        if (batch.selectedInUse) {
          for (int j = 0; j != n; j++) {
            int i = sel[j];
            outputIsNull[i] = false;
            outputVector[i] = vector1Value | vector2[i];
          }
        } else {
          Arrays.fill(outputIsNull, 0, n, false);
          for (int i = 0; i != n; i++) {
            outputVector[i] = vector1Value | vector2[i];
          }
        }
      } else if (!inputColVector1.isRepeating && inputColVector2.isRepeating) {
        if (batch.selectedInUse) {
          for (int j = 0; j != n; j++) {
            int i = sel[j];
            outputIsNull[i] = false;
            outputVector[i] = vector1[i] | vector2Value;
          }
        } else {
          Arrays.fill(outputIsNull, 0, n, false);
          for (int i = 0; i != n; i++) {
            outputVector[i] = vector1[i] | vector2Value;
          }
        }
      } else /* neither side is repeating */{
        if (batch.selectedInUse) {
          for (int j = 0; j != n; j++) {
            int i = sel[j];
            outputIsNull[i] = false;
            outputVector[i] = vector1[i] | vector2[i];
          }
        } else {
          Arrays.fill(outputIsNull, 0, n, false);
          for (int i = 0; i != n; i++) {
            outputVector[i] = vector1[i] | vector2[i];
          }
        }
      }
      return;
    }

    // Carefully handle NULLs...

    /*
     * For better performance on LONG/DOUBLE we don't want the conditional
     * statements inside the for loop.
     */
    outV.noNulls = false;

    if (inputColVector1.noNulls && !inputColVector2.noNulls) {
      // only input 2 side has nulls
      if ((inputColVector1.isRepeating) && (inputColVector2.isRepeating)) {
        // All must be selected otherwise size would be zero
        // Repeating property will not change.
        outV.isRepeating = true;
        outputVector[0] = vector1[0] | vector2[0];
        outputIsNull[0] = (vector1[0] == 0) && inputColVector2.isNull[0];
      } else if (inputColVector1.isRepeating && !inputColVector2.isRepeating) {
        if (batch.selectedInUse) {
          for (int j = 0; j != n; j++) {
            int i = sel[j];
            outputVector[i] = vector1Value | vector2[i];
            outputIsNull[i] = (vector1Value == 0) && inputColVector2.isNull[i];
          }
        } else {
          for (int i = 0; i != n; i++) {
            outputVector[i] = vector1Value | vector2[i];
            outputIsNull[i] = (vector1Value == 0) && inputColVector2.isNull[i];
          }
        }
      } else if (!inputColVector1.isRepeating && inputColVector2.isRepeating) {
        if (batch.selectedInUse) {
          for (int j = 0; j != n; j++) {
            int i = sel[j];
            outputVector[i] = vector1[i] | vector2Value;
            outputIsNull[i] = (vector1[i] == 0) && inputColVector2.isNull[0];
          }
        } else {
          for (int i = 0; i != n; i++) {
            outputVector[i] = vector1[i] | vector2Value;
            outputIsNull[i] = (vector1[i] == 0) && inputColVector2.isNull[0];
          }
        }
      } else /* neither side is repeating */{
        if (batch.selectedInUse) {
          for (int j = 0; j != n; j++) {
            int i = sel[j];
            outputVector[i] = vector1[i] | vector2[i];
            outputIsNull[i] = (vector1[i] == 0) && inputColVector2.isNull[i];
          }
        } else {
          for (int i = 0; i != n; i++) {
            outputVector[i] = vector1[i] | vector2[i];
            outputIsNull[i] = (vector1[i] == 0) && inputColVector2.isNull[i];
          }
        }
      }
    } else if (!inputColVector1.noNulls && inputColVector2.noNulls) {
      // only input 1 side has nulls
      if ((inputColVector1.isRepeating) && (inputColVector2.isRepeating)) {
        // All must be selected otherwise size would be zero
        // Repeating property will not change.
        outV.isRepeating = true;
        outputVector[0] = vector1[0] | vector2[0];
        outputIsNull[0] = inputColVector1.isNull[0] && (vector2[0] == 0);
      } else if (inputColVector1.isRepeating && !inputColVector2.isRepeating) {
        if (batch.selectedInUse) {
          for (int j = 0; j != n; j++) {
            int i = sel[j];
            outputVector[i] = vector1Value | vector2[i];
            outputIsNull[i] = inputColVector1.isNull[0] && (vector2[i] == 0);
          }
        } else {
          for (int i = 0; i != n; i++) {
            outputVector[i] = vector1Value | vector2[i];
            outputIsNull[i] = inputColVector1.isNull[0] && (vector2[i] == 0);
          }
        }
      } else if (!inputColVector1.isRepeating && inputColVector2.isRepeating) {
        if (batch.selectedInUse) {
          for (int j = 0; j != n; j++) {
            int i = sel[j];
            outputVector[i] = vector1[i] | vector2Value;
            outputIsNull[i] = inputColVector1.isNull[i] && (vector2Value == 0);
          }
        } else {
          for (int i = 0; i != n; i++) {
            outputVector[i] = vector1[i] | vector2Value;
            outputIsNull[i] = inputColVector1.isNull[i] && (vector2Value == 0);
          }
        }
      } else /* neither side is repeating */{
        if (batch.selectedInUse) {
          for (int j = 0; j != n; j++) {
            int i = sel[j];
            outputVector[i] = vector1[i] | vector2[i];
            outputIsNull[i] = inputColVector1.isNull[i] && (vector2[i] == 0);
          }
        } else {
          for (int i = 0; i != n; i++) {
            outputVector[i] = vector1[i] | vector2[i];
            outputIsNull[i] = inputColVector1.isNull[i] && (vector2[i] == 0);
          }
        }
      }
    } else /* !inputColVector1.noNulls && !inputColVector2.noNulls */{
      // either input 1 or input 2 may have nulls
      if ((inputColVector1.isRepeating) && (inputColVector2.isRepeating)) {
        // All must be selected otherwise size would be zero
        // Repeating property will not change.
        outV.isRepeating = true;
        outputVector[0] = vector1[0] | vector2[0];
        outputIsNull[0] = ((vector1[0] == 0) && inputColVector2.isNull[0])
            || (inputColVector1.isNull[0] && (vector2[0] == 0))
            || (inputColVector1.isNull[0] && inputColVector2.isNull[0]);
      } else if (inputColVector1.isRepeating && !inputColVector2.isRepeating) {
        if (batch.selectedInUse) {
          for (int j = 0; j != n; j++) {
            int i = sel[j];
            outputVector[i] = vector1Value | vector2[i];
            outputIsNull[i] = ((vector1[0] == 0) && inputColVector2.isNull[i])
                || (inputColVector1.isNull[0] && (vector2[i] == 0))
                || (inputColVector1.isNull[0] && inputColVector2.isNull[i]);
          }
        } else {
          for (int i = 0; i != n; i++) {
            outputVector[i] = vector1Value | vector2[i];
            outputIsNull[i] = ((vector1[0] == 0) && inputColVector2.isNull[i])
                || (inputColVector1.isNull[0] && (vector2[i] == 0))
                || (inputColVector1.isNull[0] && inputColVector2.isNull[i]);
          }
        }
      } else if (!inputColVector1.isRepeating && inputColVector2.isRepeating) {
        if (batch.selectedInUse) {
          for (int j = 0; j != n; j++) {
            int i = sel[j];
            outputVector[i] = vector1[i] | vector2Value;
            outputIsNull[i] = ((vector1[i] == 0) && inputColVector2.isNull[0])
                || (inputColVector1.isNull[i] && (vector2[0] == 0))
                || (inputColVector1.isNull[i] && inputColVector2.isNull[0]);
          }
        } else {
          for (int i = 0; i != n; i++) {
            outputVector[i] = vector1[i] | vector2Value;
            outputIsNull[i] = ((vector1[i] == 0) && inputColVector2.isNull[0])
                || (inputColVector1.isNull[i] && (vector2[0] == 0))
                || (inputColVector1.isNull[i] && inputColVector2.isNull[0]);
          }
        }
      } else /* neither side is repeating */{
        if (batch.selectedInUse) {
          for (int j = 0; j != n; j++) {
            int i = sel[j];
            outputVector[i] = vector1[i] | vector2[i];
            outputIsNull[i] = ((vector1[i] == 0) && inputColVector2.isNull[i])
                || (inputColVector1.isNull[i] && (vector2[i] == 0))
                || (inputColVector1.isNull[i] && inputColVector2.isNull[i]);
          }
        } else {
          for (int i = 0; i != n; i++) {
            outputVector[i] = vector1[i] | vector2[i];
            outputIsNull[i] = ((vector1[i] == 0) && inputColVector2.isNull[i])
                || (inputColVector1.isNull[i] && (vector2[i] == 0))
                || (inputColVector1.isNull[i] && inputColVector2.isNull[i]);
          }
        }
      }
    }
  }

  @Override
  public int getOutputColumn() {
    return outputColumn;
  }

  public int getColNum1() {
    return colNum1;
  }

  public void setColNum1(int colNum1) {
    this.colNum1 = colNum1;
  }

  public int getColNum2() {
    return colNum2;
  }

  public void setColNum2(int colNum2) {
    this.colNum2 = colNum2;
  }

  public void setOutputColumn(int outputColumn) {
    this.outputColumn = outputColumn;
  }

  @Override
  public String vectorExpressionParameters() {
    return "col " + colNum1 + ", col " + colNum2;
  }

  @Override
  public VectorExpressionDescriptor.Descriptor getDescriptor() {
    return (new VectorExpressionDescriptor.Builder())
        .setMode(
            VectorExpressionDescriptor.Mode.PROJECTION)
        .setNumArguments(2)
        .setArgumentTypes(
            VectorExpressionDescriptor.ArgumentType.getType("long"),
            VectorExpressionDescriptor.ArgumentType.getType("long"))
        .setInputExpressionTypes(
            VectorExpressionDescriptor.InputExpressionType.COLUMN,
            VectorExpressionDescriptor.InputExpressionType.COLUMN).build();
  }
}
