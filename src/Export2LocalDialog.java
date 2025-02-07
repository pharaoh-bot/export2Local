import com.intellij.compiler.server.BuildManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;

public class Export2LocalDialog extends JDialog {

	public static final String JAVA_SUFFIX = ".java";
	private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;

    private JTextField textField;
    private JButton fileChooseBtn;
    private JPanel filePanel;
	private JCheckBox javaCheckBox;
	private JCheckBox classCheckBox;
	private JProgressBar progressBar1;
	private JScrollPane jScrollPane;
	private AnActionEvent event;
    private JBList fieldList;

    private static final String TARGET_PATH = "/target/classes";
	private static final String SRC_PATH = "/src/main/java";
	private static final String RES_PATH = "/src/main/resources";
	private static final String SAVE_PATH_KEY = "export2Local_path";
	private static PropertiesComponent propertiesComponent =  PropertiesComponent.getInstance();

	Export2LocalDialog(final AnActionEvent event) {
        this.event = event;
        setTitle("Export2Local");

        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        textField.setText(StringUtils.isNoneBlank(propertiesComponent.getValue(SAVE_PATH_KEY))?propertiesComponent.getValue(SAVE_PATH_KEY):"" );
        buttonOK.addActionListener(e -> onOK());

        buttonCancel.addActionListener(e -> onCancel());

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
        	@Override
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(e -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        // 保存路径按钮事件
        fileChooseBtn.addActionListener(e -> {

			String userDir = System.getProperty("user.home");
			JFileChooser fileChooser = new JFileChooser(userDir + "/Desktop");
			fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int flag = fileChooser.showOpenDialog(null);
			if (flag == JFileChooser.APPROVE_OPTION) {
				String absolutePath = fileChooser.getSelectedFile().getAbsolutePath();
				textField.setText(absolutePath);
				propertiesComponent.setValue(SAVE_PATH_KEY,absolutePath);
			}
		});

    }

    private void onOK() {
        // 条件校验
        if (null == textField.getText() || "".equals(textField.getText())) {
            Messages.showErrorDialog(this, "Please Select Save Path!", "Error");
            return;
        }

        ListModel<VirtualFile> model = fieldList.getModel();
        if (model.getSize() == 0) {
            Messages.showErrorDialog(this, "Please Select Export File!", "Error");
            return;
        }
		boolean javaCheckBoxSelected = javaCheckBox.isSelected();
		boolean classCheckBoxSelected = classCheckBox.isSelected();
        try {
			progressBar1.setVisible(true);
			progressBar1.paintImmediately(progressBar1.getBounds());
			ModuleManager moduleManager = ModuleManager.getInstance(event.getProject());
			Module[] modules = moduleManager.getModules();
			String moduleName;
			String srcPath;
			String resPath;
			// 模块对象
			int moduleSize = modules.length;
			for (Module module : modules) {
				String projectName = module.getProject().getName();
				moduleName = module.getName();
				//多模块项目中排除项目名和模块名相同的模块
				if(moduleSize > 1 && projectName.equals(moduleName)){
					continue;
				}
				srcPath = moduleName + SRC_PATH;
				resPath = moduleName + RES_PATH;
				// 导出目录
				String exportPath = textField.getText() + "/" + moduleName;

				for (int i = 0; i < model.getSize(); i++) {
					VirtualFile element = model.getElementAt(i);
					String elementPath = element.getPath();
					if(elementPath.indexOf(moduleName)!=-1){
						//判断是否为java文件或目录
						int srcPathPos = elementPath.indexOf(srcPath);
						if (srcPathPos != -1) {
							String packPath = StringUtils.substring(elementPath, srcPathPos + srcPath.length() + 1);
							//src
							if(javaCheckBoxSelected){
								exportJavaSource(elementPath, exportPath + "/src/main/java/" + packPath);
							}
							//class
							if(classCheckBoxSelected){
								exportJavaClass(TARGET_PATH, elementPath, exportPath + TARGET_PATH + "/" + packPath);
							}
						} else {
							int resPathPos = elementPath.indexOf(resPath);
							String packPath = StringUtils.substring(elementPath, elementPath.indexOf(moduleName) + moduleName.length() + 1);
							String toResPath = exportPath + "/" + packPath;
							if (resPathPos != -1) {
								if (classCheckBoxSelected) {
									toResPath = (exportPath + "/" + packPath).replace(resPath.replace(moduleName, ""), TARGET_PATH);
									FileUtil.copyFileOrDir(new File(elementPath), new File(toResPath));
								}
								if (javaCheckBoxSelected) {
									toResPath = exportPath + "/" + packPath;
									FileUtil.copyFileOrDir(new File(elementPath), new File(toResPath));
								}
							} else {
								FileUtil.copyFileOrDir(new File(elementPath), new File(toResPath));
							}
						}
					}
				}
			}
			progressBar1.setVisible(false);
			progressBar1.paintImmediately(progressBar1.getBounds());
			Messages.showInfoMessage(this,"Export Successful!","Info");
		} catch (Exception e) {
			e.printStackTrace();
			progressBar1.setVisible(false);
			progressBar1.paintImmediately(progressBar1.getBounds());
			String errorMsg = "Export to Local Error! ";
			if(classCheckBoxSelected){
				errorMsg += "[Probably not compiled]";

			}
			Messages.showErrorDialog(this, errorMsg, "Error");
        }

        dispose();
    }

	/**
	 * export  class file
	 * @param targetPath
	 * @param elementPath
	 * @param packPath
	 * @throws IOException
	 */
	private void exportJavaClass(String targetPath,String elementPath, String packPath) throws IOException {
			String toTargetPath = StringUtils.replace(elementPath, SRC_PATH, targetPath);
			if(toTargetPath.endsWith(JAVA_SUFFIX)){
				String classPath = StringUtils.substring(toTargetPath,0,toTargetPath.lastIndexOf("/")+1);
				final String className = StringUtils.substring(toTargetPath,toTargetPath.lastIndexOf("/")+1,toTargetPath.lastIndexOf("."));
				String[] list = new File(classPath).list((dir, name) -> name.startsWith(className));
				String localPath;
				for(String cname:list){
					localPath = StringUtils.substring(packPath, 0, packPath.lastIndexOf("/")+1) + cname;
					FileUtil.copyFileOrDir(new File(classPath+cname), new File(localPath));
				}
			}else{
				FileUtil.copyFileOrDir(new File(toTargetPath),new File(packPath));
			}
	}

	/**
	 * export java file
	 * @param elementPath
	 * @param packPath
	 * @throws IOException
	 */
	private void exportJavaSource(String elementPath, String packPath) throws IOException {
			File srcFrom = new File(elementPath);
			File srcTo = new File(packPath);
			FileUtil.copyFileOrDir(srcFrom, srcTo);
	}

	private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    private void createUIComponents() {
        VirtualFile[] data = event.getData(LangDataKeys.VIRTUAL_FILE_ARRAY);
        fieldList = new JBList(data);


        // 测试匹配项目module
        Module[] modules = event.getData(LangDataKeys.MODULE_CONTEXT_ARRAY);
        for (Module module : modules) {
            ModuleManager instance = ModuleManager.getInstance(event.getProject());
            // target/classes
            CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
            VirtualFile compilerOutputPath = compilerModuleExtension.getCompilerOutputPath();

            // 获取artifact
            // 1.war
            // 2.exploded-war
            ArtifactManager.getInstance(event.getProject()).getArtifacts()[1].getArtifactType().getId();

        }
        fieldList.setEmptyText("No File Selected!");
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(fieldList);
        filePanel = decorator.createPanel();
    }
}
