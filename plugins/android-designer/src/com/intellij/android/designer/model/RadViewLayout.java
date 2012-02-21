/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.model;

import com.intellij.android.designer.designSurface.CreateOperation;
import com.intellij.android.designer.designSurface.MoveOperation;
import com.intellij.android.designer.designSurface.TreeOperation;
import com.intellij.designer.componentTree.TreeEditOperation;
import com.intellij.designer.designSurface.ComponentDecorator;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.selection.NonResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadLayout;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class RadViewLayout extends RadLayout {
  private final RadViewComponent myContainer;

  public RadViewLayout(RadViewComponent container) {
    myContainer = container;
  }

  @Override
  public ComponentDecorator getChildSelectionDecorator(RadComponent component) {
    return new NonResizeSelectionDecorator(Color.RED, 1);
  }

  @Override
  public EditOperation processChildOperation(OperationContext context) {
    if (context.getArea().isTree()) {
      if (!myContainer.getChildren().isEmpty() && TreeEditOperation.isTarget(myContainer, context)) {
        /*if ("TableRow".equals(myContainer.getTitle())) {
          return null;
        }*/
        return new TreeOperation(myContainer, context);
      }
      return null;
    }
    if (context.isMove()) {
      return new MoveOperation(context);
    }
    if (context.isCreate()) {
      RadViewComponent component = (RadViewComponent)context.getComponents().get(0);
      if ("android".equals(component.getTitle())) {
        return new CreateOperation(myContainer, context);
      }
    }
    return null;
  }
}