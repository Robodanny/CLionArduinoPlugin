package com.vladsch.clionarduinoplugin.settings;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.Alarm;
import com.vladsch.clionarduinoplugin.util.ApplicationSettingsListener;
import com.vladsch.plugin.util.RecursionGuard;
import com.vladsch.plugin.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.ItemSelectable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;

public class NewProjectSettingsForm extends FormParams<ArduinoApplicationSettings> implements Disposable, ApplicationSettingsListener, SettableForm<ArduinoApplicationSettings> {
    private JPanel myMainPanel;
    JComboBox<String> myLanguageVersion;
    JComboBox<String> myLibraryType;
    JComboBox<String> myBoards;
    private JLabel myCpuLabel;
    JComboBox<String> myCpus;
    JComboBox<String> myProgrammers;
    JBCheckBox myAddLibraryDirectory;
    JBCheckBox myVerbose;
    TextFieldWithHistory myPort;
    JBCheckBox myCommentOutUnusedSettings;

    JComboBox<String> myLibraryCategory;
    JTextField myLibraryDirectory;
    JTextField myLibraryDisplayName;
    JTextField myAuthorName;
    JTextField myAuthorEMail;
    private JLabel myLibraryCategoryLabel;
    private JLabel myLibraryTypeLabel;
    private JLabel myAuthorNameLabel;
    private JLabel myAuthorEMailLabel;
    JComboBox<String> myBaudRate;
    private JLabel myLibraryDisplayNameLabel;

    final Set<Object> myPendingUpdates;

    private Alarm myUpdate;
    RecursionGuard myRecursionGuard = new RecursionGuard();

    private final SettingsComponents<ArduinoApplicationSettings> components;
    final boolean myIsLibrary;
    private final boolean isImmediateUpdate;
    private final boolean isLimitedConfig;
    Runnable myRunnable = null;

    public enum ErrorComp {
        CPU,
        BOARD,
        LIB_DIR,
    }

    @Nullable
    public JComponent getErrorComponent(ErrorComp comp) {
        switch (comp) {
            case CPU:
                return myCpus;
            case BOARD:
                return myBoards;
            case LIB_DIR:
                return myLibraryDirectory;

            default:
                return null;
        }
    }

    @Nullable
    public JComponent getPreferredFocusedComponent() {
        return myBoards;
    }

    public NewProjectSettingsForm(ArduinoApplicationSettingsProxy settings, boolean immediateUpdate, final boolean limitedConfig) {
        super(settings.getApplicationSettings());

        myIsLibrary = settings.isLibrary();
        isImmediateUpdate = immediateUpdate;
        isLimitedConfig = limitedConfig;

        myUpdate = new Alarm(this);
        myPendingUpdates = new LinkedHashSet<>();

        components = new SettingsComponents<ArduinoApplicationSettings>(mySettings) {
            @Override
            protected Settable[] createComponents(ArduinoApplicationSettings i) {
                if (myIsLibrary) {
                    return new Settable[] {
                            componentString(SerialPortNames.ADAPTER, myPort, i::getPort, i::setPort),
                            component(SerialBaudRates.ADAPTER, myBaudRate, i::getBaudRate, i::setBaudRate),
                            componentString(BoardNames.ADAPTER, myBoards, i::getBoardName, i::setBoardName),
                            componentString(CpuNames.ADAPTER, myCpus, i::getCpuName, i::setCpuName),
                            componentString(ProgrammerNames.ADAPTER, myProgrammers, i::getProgrammerName, i::setProgrammerName),
                            component(myAddLibraryDirectory, i::isAddLibraryDirectory, i::setAddLibraryDirectory),
                            component(myLibraryDirectory, i::getLibraryDirectory, i::setLibraryDirectory),
                            component(myVerbose, i::isVerbose, i::setVerbose),
                            component(myCommentOutUnusedSettings, i::isCommentUnusedSettings, i::setCommentUnusedSettings),
                            componentString(LanguageVersionNames.ADAPTER, myLanguageVersion, i::getLanguageVersionName, i::setLanguageVersionName),

                            // library only
                            component(myLibraryDisplayName, i::getLibraryDisplayName, i::setLibraryDisplayName),
                            componentString(LibraryTypeNames.ADAPTER, myLibraryType, i::getLibraryType, i::setLibraryType),
                            componentString(LibraryCategoryNames.ADAPTER, myLibraryCategory, i::getLibraryCategory, i::setLibraryCategory),
                            component(myAuthorName, i::getAuthorName, i::setAuthorName),
                            component(myAuthorEMail, i::getAuthorEMail, i::setAuthorEMail),
                    };
                } else {
                    return new Settable[] {
                            componentString(SerialPortNames.ADAPTER, myPort, i::getPort, i::setPort),
                            component(SerialBaudRates.ADAPTER, myBaudRate, i::getBaudRate, i::setBaudRate),
                            componentString(BoardNames.ADAPTER, myBoards, i::getBoardName, i::setBoardName),
                            componentString(CpuNames.ADAPTER, myCpus, i::getCpuName, i::setCpuName),
                            componentString(ProgrammerNames.ADAPTER, myProgrammers, i::getProgrammerName, i::setProgrammerName),
                            component(myAddLibraryDirectory, i::isAddLibraryDirectory, i::setAddLibraryDirectory),
                            component(myLibraryDirectory, i::getLibraryDirectory, i::setLibraryDirectory),
                            component(myVerbose, i::isVerbose, i::setVerbose),
                            component(myCommentOutUnusedSettings, i::isCommentUnusedSettings, i::setCommentUnusedSettings),
                            componentString(LanguageVersionNames.ADAPTER, myLanguageVersion, i::getLanguageVersionName, i::setLanguageVersionName),
                    };
                }
            }
        };

        ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(ApplicationSettingsListener.TOPIC, this);

        ItemListener itemListener = new ItemListener() {
            @Override
            public void itemStateChanged(final ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    applyChanges(e.getSource());
                }
            }
        };

        ActionListener actionListener = new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                applyChanges(e.getSource());
            }
        };

        components.forAllComponents(mySettings, settable -> {
            Object target = settable.getComponent();
            if (target instanceof AbstractButton) {
                ((AbstractButton) target).addActionListener(actionListener);
            } else {
                if (target instanceof ItemSelectable) {
                    ((ItemSelectable) target).addItemListener(itemListener);
                }

                if (target instanceof JTextComponent) {
                    ((JTextComponent) target).getDocument().addDocumentListener(new DocumentAdapter() {
                        @Override
                        protected void textChanged(@NotNull final DocumentEvent e) {
                            applyChanges(target);
                        }
                    });
                }
            }
        });

        reset(mySettings);
        updateOptions(true);
    }

    public void setRunnable(final Runnable myRunnable) {
        this.myRunnable = myRunnable;
    }

    @Override
    public void dispose() {
        myUpdate = null;
        components.dispose();
        myPendingUpdates.clear();
    }

    static final int RESET_UPDATE = 5;
    static final int COMPONENT_UPDATE = 4;
    static final int SETTINGS_UPDATE = 0;

    void applyChanges(Object changed) {
        if (!(changed instanceof JTextComponent)) {
            boolean updateCpus = changed == myBoards;
            updateOptions(updateCpus);
        }

        if (isImmediateUpdate) {
            if (myUpdate.isDisposed()) {
                myPendingUpdates.clear();
                return;
            }

            myRecursionGuard.enter(SETTINGS_UPDATE, () -> {
                Collections.addAll(myPendingUpdates, changed);

                myUpdate.cancelAllRequests();
                myUpdate.addRequest(() -> {
                    mySettings.groupChanges(() -> {
                        Object[] targets = myPendingUpdates.toArray();
                        myPendingUpdates.clear();
                        components.apply(mySettings, targets);
                    });
                }, 250, ModalityState.any());
            });
        } else {
            if (myUpdate.isDisposed()) {
                return;
            }

            myUpdate.cancelAllRequests();
            if (myRunnable != null) {
                myUpdate.addRequest(() -> {
                    myRunnable.run();
                }, 250, ModalityState.any());
            }
        }
    }

    public void updatePort(final @NotNull ArduinoApplicationSettings settings) {
        myPort.setHistorySize(-1);
        myPort.setHistory(SerialPortNames.getDisplayNames());
        myPort.setText(settings.getPort());
    }

    void updateOptions(boolean updateCpus) {
        myRecursionGuard.enter(COMPONENT_UPDATE, () -> {
            boolean isArduinoLibrary = myIsLibrary && ArduinoProjectFileSettings.ARDUINO_LIB_TYPE.equals(myLibraryType.getSelectedItem());
            myLibraryTypeLabel.setVisible(myIsLibrary && !isLimitedConfig);
            myLibraryType.setVisible(myIsLibrary && !isLimitedConfig);

            myLibraryCategoryLabel.setVisible(isArduinoLibrary && !isLimitedConfig);
            myLibraryCategory.setVisible(isArduinoLibrary && !isLimitedConfig);

            myAuthorNameLabel.setVisible(isArduinoLibrary && !isLimitedConfig);
            myAuthorName.setVisible(isArduinoLibrary && !isLimitedConfig);

            myAuthorEMailLabel.setVisible(isArduinoLibrary && !isLimitedConfig);
            myAuthorEMail.setVisible(isArduinoLibrary && !isLimitedConfig);

            myLibraryDisplayName.setVisible(isArduinoLibrary && !isLimitedConfig);
            myLibraryDisplayNameLabel.setVisible(isArduinoLibrary && !isLimitedConfig);

            myLibraryDirectory.setEnabled(myAddLibraryDirectory.isSelected());

            if (updateCpus) {
                updateCpus();
            }
        });
    }

    void updateCpus() {
        updateCpuEnum();

        //CpuNames.ADAPTER.fillComboBox(myCpus, ComboBoxAdaptable.EMPTY);
        myCpus.setSelectedItem(mySettings.getCpuName());

        myCpuLabel.setEnabled(CpuNames.values.length > 1);
        myCpus.setEnabled(CpuNames.values.length > 1);
    }

    void updateEnums() {
        SerialPortNames.updateValues(true, null);
        BoardNames.updateValues(mySettings.getBoardNames(), false, myBoards);
        ProgrammerNames.updateValues(mySettings.getProgrammerNames(), true, myProgrammers);
    }

    void updateCpuEnum() {
        if (myBoards != null && myBoards.getSelectedItem() != null) {
            CpuNames.updateValues(mySettings.getBoardCpuNames((String) myBoards.getSelectedItem()), true, myCpus);
        }
    }

    private void createUIComponents() {
        updateEnums();

        LibraryTypeNames.updateValues(myLibraryType);
        LanguageVersionNames.updateValues(myLanguageVersion);
        LibraryCategoryNames.updateValues(myLibraryCategory);

        myLanguageVersion = LanguageVersionNames.ADAPTER.createComboBox();
        myLibraryType = LibraryTypeNames.ADAPTER.createComboBox();

        myBoards = BoardNames.ADAPTER.createComboBox();
        myBaudRate = SerialBaudRates.ADAPTER.createComboBox();

        updateCpuEnum();
        myCpus = CpuNames.ADAPTER.createComboBox();

        myProgrammers = ProgrammerNames.ADAPTER.createComboBox();

        myLibraryCategory = LibraryCategoryNames.ADAPTER.createComboBox();
    }

    @Override
    public void onSettingsChanged(ArduinoApplicationSettings settings) {
        if (mySettings == settings) {
            reset(mySettings);
        }
    }

    public boolean isModified(@NotNull ArduinoApplicationSettings settings) {
        return components.isModified(settings);
    }

    public void apply(@NotNull ArduinoApplicationSettings settings) {
        if (isModified(settings)) {
            if (settings == mySettings) {
                myRecursionGuard.enter(SETTINGS_UPDATE, () -> {
                    settings.groupChanges(() -> {
                        components.apply(settings);
                    });
                });
            } else {
                settings.groupChanges(() -> {
                    components.apply(settings);
                });
            }
        }
    }

    public void reset(@NotNull ArduinoApplicationSettings settings) {
        // always set to settings, but prevent callbacks to modify settings
        if (myRecursionGuard.enter(RESET_UPDATE, () -> {
            unguardedReset(settings);
        })) {
            // after reset we update options
            updateOptions(false);
        }
    }

    void unguardedReset(final @NotNull ArduinoApplicationSettings settings) {
        components.reset(settings);

        updateEnums();

        myCpuLabel.setText(mySettings.getCpuLabel());

        //BoardNames.ADAPTER.fillComboBox(myBoards, ComboBoxAdaptable.EMPTY);
        myBoards.setSelectedItem(settings.getBoardName());

        updateCpus();

        //ProgrammerNames.ADAPTER.fillComboBox(myProgrammers, ComboBoxAdaptable.EMPTY);
        myProgrammers.setSelectedItem(settings.getProgrammerName());

        //LibraryCategoryNames.ADAPTER.fillComboBox(myLibraryCategory, ComboBoxAdaptable.EMPTY);
        myLibraryCategory.setSelectedItem(settings.getLibraryCategory());

        updatePort(settings);
    }

    public JComponent getComponent() {
        return myMainPanel;
    }
}
