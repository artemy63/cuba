/*
 * Copyright (c) 2011 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */

package com.haulmont.cuba.desktop.gui.components.filter;

import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.cuba.core.entity.CategorizedEntity;
import com.haulmont.cuba.core.global.MessageProvider;
import com.haulmont.cuba.core.global.UserSessionProvider;
import com.haulmont.cuba.desktop.App;
import com.haulmont.cuba.gui.components.filter.AbstractConditionDescriptor;
import com.haulmont.cuba.gui.components.filter.AbstractCustomConditionDescriptor;
import com.haulmont.cuba.gui.components.filter.AbstractFilterEditor;
import com.haulmont.cuba.gui.components.filter.GroupType;
import com.haulmont.cuba.gui.components.filter.addcondition.*;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import net.miginfocom.swing.MigLayout;

import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog to select a new generic filter condition.
 *
 * <p>$Id$</p>
 *
 * @author krivopustov
 */
public class AddConditionDlg extends JDialog {

    private SelectionHandler selectionHandler;
    private JTree tree;
    private JButton okBtn;

    public AddConditionDlg(MetaClass metaClass,
                           List<AbstractConditionDescriptor> propertyDescriptors,
                           DescriptorBuilder descriptorBuilder,
                           SelectionHandler selectionHandler)
    {
        super(App.getInstance().getMainFrame());
        this.selectionHandler = selectionHandler;

        setTitle(MessageProvider.getMessage(AbstractFilterEditor.MESSAGES_PACK, "FilterEditor.addCondition"));
        setSize(400, 300);
        setResizable(false);
        setLocationRelativeTo(App.getInstance().getMainFrame());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                close();
            }
        });

        CommitAction commitAction = new CommitAction();
        CancelAction cancelAction = new CancelAction();

        setLayout(new MigLayout("fill", "[grow][]", "[grow][]"));

        Model model = new Model(metaClass, propertyDescriptors, descriptorBuilder);
        tree = new JTree(model) {
            @Override
            public String convertValueToText(Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                if (value instanceof ModelItem)
                    return ((ModelItem) value).getCaption();
                else
                    return "???";
            }
        };
        tree.setRootVisible(false);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setExpandsSelectedPaths(true);
        add(new JScrollPane(tree), "wrap, spanx 2, grow");

        okBtn = new JButton(MessageProvider.getMessage(getClass(), "actions.Ok"));
        add(okBtn, "align right");
        okBtn.addActionListener(commitAction);

        JButton cancelBtn = new JButton(MessageProvider.getMessage(getClass(), "actions.Cancel"));
        add(cancelBtn);
        cancelBtn.addActionListener(cancelAction);

        tree.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ctrl ENTER"), "commit");
        tree.getActionMap().put("commit", commitAction);
        tree.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");
        tree.getActionMap().put("cancel", new CancelAction());

        tree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                Object pathComponent = e.getNewLeadSelectionPath().getLastPathComponent();
                okBtn.setEnabled(pathComponent instanceof ModelItem && ((ModelItem) pathComponent).getDescriptor() != null);
            }
        });

        tree.setSelectionPath(new TreePath(new Object[] { model.root, model.root.getChildren().get(0) }));
        tree.requestFocus();
    }

    private void close() {
        setVisible(false);
        App.getInstance().enable();
    }

    private class CommitAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (selectionHandler != null) {
                Object pathComponent = tree.getLastSelectedPathComponent();
                if (pathComponent instanceof ModelItem && ((ModelItem) pathComponent).getDescriptor() != null) {
                    selectionHandler.select(((ModelItem) pathComponent).getDescriptor());
                    close();
                }
            }
        }
    }

    private class CancelAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            close();
        }
    }

    private static class Model implements TreeModel {

        private MetaClass metaClass;
        private List<AbstractConditionDescriptor> propertyDescriptors;
        private DescriptorBuilder descriptorBuilder;

        private List<ModelItem> rootModelItems;
        private ModelItem root;

        public Model(MetaClass metaClass,
                     List<AbstractConditionDescriptor> propertyDescriptors,
                     DescriptorBuilder descriptorBuilder)
        {
            this.metaClass = metaClass;
            this.propertyDescriptors = propertyDescriptors;
            this.descriptorBuilder = descriptorBuilder;
            initRoot();
        }

        private void initRoot() {
            rootModelItems = new ArrayList<ModelItem>();

            rootModelItems.add(new RootPropertyModelItem(metaClass, propertyDescriptors, descriptorBuilder));

            for (AbstractConditionDescriptor descriptor : propertyDescriptors) {
                if (descriptor instanceof AbstractCustomConditionDescriptor) {
                    rootModelItems.add(new RootCustomConditionModelItem(propertyDescriptors));
                    break;
                }
            }

            rootModelItems.add(new RootGroupingModelItem(descriptorBuilder));

            if (CategorizedEntity.class.isAssignableFrom(metaClass.getJavaClass())) {
                rootModelItems.add(new RootRuntimePropertiesModelItem(descriptorBuilder));
            }

            if (UserSessionProvider.getUserSession().isSpecificPermitted("cuba.gui.filter.customConditions")) {
                rootModelItems.add(new NewCustomConditionModelItem(descriptorBuilder));
            }

            root = new ModelItem() {
                @Override
                public ModelItem getParent() {
                    return null;
                }

                @Nonnull
                @Override
                public List<ModelItem> getChildren() {
                    return rootModelItems;
                }

                @Override
                public String getCaption() {
                    return "root";
                }

                @Override
                public AbstractConditionDescriptor getDescriptor() {
                    return null;
                }
            };
        }

        @Override
        public Object getRoot() {
            return root;
        }

        @Override
        public Object getChild(Object parent, int index) {
            return ((ModelItem) parent).getChildren().get(index);
        }

        @Override
        public int getChildCount(Object parent) {
            return ((ModelItem) parent).getChildren().size();
        }

        @Override
        public boolean isLeaf(Object node) {
            return ((ModelItem) node).getChildren().isEmpty();
        }

        @Override
        public void valueForPathChanged(TreePath path, Object newValue) {
        }

        @Override
        public int getIndexOfChild(Object parent, Object child) {
            return ((ModelItem) parent).getChildren().indexOf(child);
        }

        @Override
        public void addTreeModelListener(TreeModelListener l) {
        }

        @Override
        public void removeTreeModelListener(TreeModelListener l) {
        }
    }

    public static class DescriptorBuilder extends AbstractDescriptorBuilder {

        public DescriptorBuilder(String messagesPack, String filterComponentName, CollectionDatasource datasource) {
            super(messagesPack, filterComponentName, datasource);
        }

        public PropertyConditionDescriptor buildPropertyConditionDescriptor(String name, String caption) {
            return new PropertyConditionDescriptor(name, caption, messagesPack, filterComponentName, datasource);
        }

        public GroupCreator buildGroupConditionDescriptor(GroupType groupType) {
            return new GroupCreator(groupType, filterComponentName, datasource);
        }

        public ConditionCreator buildCustomConditionDescriptor() {
            return new ConditionCreator(filterComponentName, datasource);
        }

        public RuntimePropConditionCreator buildRuntimePropConditionDescriptor() {
            return new RuntimePropConditionCreator(filterComponentName, datasource);
        }
    }
}
