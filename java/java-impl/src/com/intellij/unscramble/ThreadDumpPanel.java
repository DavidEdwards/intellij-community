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
package com.intellij.unscramble;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.intellij.icons.AllIcons.Debugger.ThreadStates.*;

/**
 * @author Jeka
 * @author Konstantin Bulenkov
 */
public class ThreadDumpPanel extends JPanel {
  private static final Icon PAUSE_ICON_DAEMON = new LayeredIcon(Paused, Daemon_sign);
  private static final Icon LOCKED_ICON_DAEMON = new LayeredIcon(Locked, Daemon_sign);
  private static final Icon RUNNING_ICON_DAEMON = new LayeredIcon(Running, Daemon_sign);
  private static final Icon SOCKET_ICON_DAEMON = new LayeredIcon(Socket, Daemon_sign);
  private static final Icon IDLE_ICON_DAEMON = new LayeredIcon(Idle, Daemon_sign);
  private static final Icon EDT_BUSY_ICON_DAEMON = new LayeredIcon(EdtBusy, Daemon_sign);
  private static final Icon IO_ICON_DAEMON = new LayeredIcon(IO, Daemon_sign);
  private final JBList myThreadList;
  private final List<ThreadState> myThreadDump;

  public ThreadDumpPanel(final Project project, final ConsoleView consoleView, final DefaultActionGroup toolbarActions, final List<ThreadState> threadDump) {
    super(new BorderLayout());
    myThreadDump = threadDump;
    final ThreadState[] data = threadDump.toArray(new ThreadState[threadDump.size()]);
    final DefaultListModel model = new DefaultListModel();

    final SearchTextField filterField = new SearchTextField();

    myThreadList = new JBList(model);
    myThreadList.setCellRenderer(new ThreadListCellRenderer());
    myThreadList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myThreadList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        int index = myThreadList.getSelectedIndex();
        if (index >= 0) {
          ThreadState selection = (ThreadState)myThreadList.getModel().getElementAt(index);
          AnalyzeStacktraceUtil.printStacktrace(consoleView, selection.getStackTrace());
        }
        else {
          AnalyzeStacktraceUtil.printStacktrace(consoleView, "");
        }
        myThreadList.repaint();
      }
    });

    DocumentAdapter filterListener = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        String text = filterField.getText();
        model.clear();
        for (ThreadState state : data) {
          if (StringUtil.containsIgnoreCase(state.getStackTrace(), text) || StringUtil.containsIgnoreCase(state.getName(), text)) {
            //noinspection unchecked
            model.addElement(state);
          }
        }
        if (!model.isEmpty()) {
          myThreadList.setSelectedIndex(0);
        }
        myThreadList.revalidate();
        myThreadList.repaint();
      }
    };
    filterField.addDocumentListener(filterListener);
    filterListener.changedUpdate(null);

    toolbarActions.add(new CopyToClipboardAction(threadDump, project));
    toolbarActions.add(new SortThreadsAction());
    //toolbarActions.add(new ShowRecentlyChanged());
    add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false).getComponent(), BorderLayout.WEST);
    
    JPanel filterPanel = new JPanel(new BorderLayout());
    filterPanel.add(new JLabel("Filter:"), BorderLayout.WEST);
    filterPanel.add(filterField);
    
    JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(filterPanel, BorderLayout.NORTH);
    leftPanel.add(ScrollPaneFactory.createScrollPane(myThreadList, SideBorder.LEFT | SideBorder.RIGHT), BorderLayout.CENTER);

    final Splitter splitter = new Splitter(false, 0.3f);
    splitter.setFirstComponent(leftPanel);
    splitter.setSecondComponent(consoleView.getComponent());

    add(splitter, BorderLayout.CENTER);

    new ListSpeedSearch(myThreadList).setComparator(new SpeedSearchComparator(false, true));

    final Editor editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext(consoleView.getPreferredFocusableComponent()));
    if (editor != null) {
      editor.getDocument().addDocumentListener(new com.intellij.openapi.editor.event.DocumentAdapter() {
        @Override
        public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
          String filter = filterField.getText();
          if (StringUtil.isNotEmpty(filter)) {
            highlightOccurrences(filter, project, editor);
          }
        }
      });
    }
  }

  private static void highlightOccurrences(String filter, Project project, Editor editor) {
    final HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorManager = EditorColorsManager.getInstance();
    final TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);
    String documentText = editor.getDocument().getText();
    int i = -1;
    while (true) {
      int nextOccurrence = StringUtil.indexOfIgnoreCase(documentText, filter, i + 1);
      if (nextOccurrence < 0) {
        break;
      }
      i = nextOccurrence;
      highlightManager.addOccurrenceHighlight(editor, i, i + filter.length(), attributes,
                                               HighlightManager.HIDE_BY_TEXT_CHANGE, null, null);
    }
  }

  private static Icon getThreadStateIcon(final ThreadState threadState) {
    final boolean daemon = threadState.isDaemon();
    if (threadState.isSleeping()) {
      return daemon ? PAUSE_ICON_DAEMON : Paused;
    }
    if (threadState.isWaiting()) {
      return daemon ? LOCKED_ICON_DAEMON : Locked;
    }
    if (threadState.getOperation() == ThreadOperation.Socket) {
      return daemon ? SOCKET_ICON_DAEMON : Socket;
    }
    if (threadState.getOperation() == ThreadOperation.IO) {
      return daemon ? IO_ICON_DAEMON : IO;
    }
    if (threadState.isEDT()) {
      if ("idle".equals(threadState.getThreadStateDetail())) {
        return daemon ? IDLE_ICON_DAEMON : Idle;
      }
      return daemon ? EDT_BUSY_ICON_DAEMON : EdtBusy;
    }
    return daemon ? RUNNING_ICON_DAEMON : Running;
  }

  private enum StateCode {RUN, RUN_IO, RUN_SOCKET, PAUSED, LOCKED, EDT, IDLE}
  private static StateCode getThreadStateCode(final ThreadState state) {
    if (state.isSleeping()) return StateCode.PAUSED;
    if (state.isWaiting()) return StateCode.LOCKED;
    if (state.getOperation() == ThreadOperation.Socket) return StateCode.RUN_SOCKET;
    if (state.getOperation() == ThreadOperation.IO) return StateCode.RUN_IO;
    if (state.isEDT()) {
      return "idle".equals(state.getThreadStateDetail()) ? StateCode.IDLE : StateCode.EDT;
    }
    return StateCode.RUN;
  }

  private static SimpleTextAttributes getAttributes(final ThreadState threadState) {
    if (threadState.isSleeping()) {
      return SimpleTextAttributes.GRAY_ATTRIBUTES;
    }
    if (threadState.isEmptyStackTrace() || ThreadDumpParser.isKnownJdkThread(threadState)) {
      return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.GRAY.brighter());
    }
    if (threadState.isEDT()) {
      return SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
    }
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  private static class ThreadListCellRenderer extends ColoredListCellRenderer {

    protected void customizeCellRenderer(final JList list, final Object value, final int index, final boolean selected, final boolean hasFocus) {
      ThreadState threadState = (ThreadState) value;
      setIcon(getThreadStateIcon(threadState));
      if (!selected) {
        ThreadState selectedThread = (ThreadState)list.getSelectedValue();
        if (threadState.isDeadlocked()) {
          setBackground(LightColors.RED);
        }
        else if (selectedThread != null && threadState.isAwaitedBy(selectedThread)) {
          setBackground(JBColor.YELLOW);
        }
        else {
          setBackground(UIUtil.getListBackground());
        }
      }
      SimpleTextAttributes attrs = getAttributes(threadState);
      append(threadState.getName() + " (", attrs);
      String detail = threadState.getThreadStateDetail();
      if (detail == null) {
        detail = threadState.getState();
      }
      if (detail.length() > 30) {
        detail = detail.substring(0, 30) + "...";
      }
      append(detail, attrs);
      append(")", attrs);
      if (threadState.getExtraState() != null) {
        append(" [" + threadState.getExtraState() + "]", attrs);
      }
    }
  }

  public void selectStackFrame(int index) {
    myThreadList.setSelectedIndex(index);
  }

  private class SortThreadsAction extends DumbAwareAction {
    private final Comparator<ThreadState> BY_TYPE = new Comparator<ThreadState>() {
      public int compare(ThreadState o1, ThreadState o2) {
        final int s1 = getThreadStateCode(o1).ordinal();
        final int s2 = getThreadStateCode(o2).ordinal();
        if (s1 == s2) {
          return o1.getName().compareToIgnoreCase(o2.getName());
        } else {
          return s1 < s2 ? - 1 :  1;
        }
      }
    };

    private final Comparator<ThreadState> BY_NAME = new Comparator<ThreadState>() {
      public int compare(ThreadState o1, ThreadState o2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    };
    private Comparator<ThreadState> COMPARATOR = BY_TYPE;
    private static final String TYPE_LABEL = "Sort threads by type";
    private static final String NAME_LABEL = "Sort threads by name";
    public SortThreadsAction() {
      super(TYPE_LABEL);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final DefaultListModel model = (DefaultListModel)myThreadList.getModel();
      final ThreadState selected = (ThreadState)myThreadList.getSelectedValue();
      ArrayList<ThreadState> states = new ArrayList<ThreadState>();
      for (int i = 0; i < model.getSize(); i++) {
        states.add((ThreadState)model.getElementAt(i));
      }
      Collections.sort(states, COMPARATOR);
      int selectedIndex = 0;
      for (int i = 0; i < states.size(); i++) {
        final ThreadState state = states.get(i);
        model.setElementAt(state, i);
        if (state == selected) {
          selectedIndex = i;
        }
      }
      myThreadList.setSelectedIndex(selectedIndex);
      COMPARATOR = COMPARATOR == BY_TYPE ? BY_NAME : BY_TYPE;
      update(e);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setIcon(COMPARATOR == BY_TYPE ? AllIcons.ObjectBrowser.SortByType : AllIcons.Icons.Inspector.SortByName);
      e.getPresentation().setText(COMPARATOR == BY_TYPE ? TYPE_LABEL : NAME_LABEL);
    }
  }
  private static class CopyToClipboardAction extends DumbAwareAction {
    private static final NotificationGroup GROUP = NotificationGroup.toolWindowGroup("Analyze thread dump", ToolWindowId.RUN, false);
    private final List<ThreadState> myThreadDump;
    private final Project myProject;

    public CopyToClipboardAction(List<ThreadState> threadDump, Project project) {
      super("Copy to Clipboard", "Copy whole thread dump to clipboard", PlatformIcons.COPY_ICON);
      myThreadDump = threadDump;
      myProject = project;
    }

    public void actionPerformed(AnActionEvent e) {
      final StringBuilder buf = new StringBuilder();
      buf.append("Full thread dump").append("\n\n");
      for (ThreadState state : myThreadDump) {
        buf.append(state.getStackTrace()).append("\n\n");
      }
      CopyPasteManager.getInstance().setContents(new StringSelection(buf.toString()));

      GROUP.createNotification("Full thread dump was successfully copied to clipboard", MessageType.INFO).notify(myProject);
    }
  }
}
