package com.example.multidoc.controller;

import com.example.multidoc.model.AnalysisResult;
import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.service.AnalysisService;
import com.example.multidoc.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Controller
@RequestMapping("/")
public class AnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisController.class);

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private DocumentService documentService;

    @GetMapping
    public String index(Model model) {
        List<AnalysisTask> tasks = analysisService.getAllTasks();
        model.addAttribute("tasks", tasks);
        return "index";
    }

    @GetMapping("/task/{id}")
    public String taskDetail(@PathVariable String id, Model model) {
        try {
            AnalysisTask task = analysisService.getTaskById(id);
            model.addAttribute("task", task);

            // 获取任务进度信息
            Map<String, Object> progress = analysisService.getTaskProgress(id);
            model.addAttribute("progress", progress);

            // 如果任务已完成，获取结果
            if (task.getStatus() == AnalysisTask.TaskStatus.COMPLETED) {
                AnalysisResult result = analysisService.getResultByTaskId(id);
                model.addAttribute("result", result);
            }

            return "task/detail";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }

    @GetMapping("/task/{id}/result")
    public String viewTaskResult(@PathVariable String id, Model model) {
        try {
            AnalysisTask task = analysisService.getTaskById(id);
            model.addAttribute("task", task);

            if (task.getStatus() == AnalysisTask.TaskStatus.COMPLETED) {
                AnalysisResult result = analysisService.getResultByTaskId(id);
                model.addAttribute("result", result);
                return "task/result";
            } else {
                model.addAttribute("error", "任务尚未完成，无法查看结果");
                return "task/detail";
            }
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }

    @PostMapping("/task/{id}/delete")
    public String deleteTask(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            analysisService.deleteTask(id);
            redirectAttributes.addFlashAttribute("message", "任务已成功删除");
            return "redirect:/";
        } catch (Exception e) {
            logger.error("删除任务失败: " + id, e);
            redirectAttributes.addFlashAttribute("error", "删除任务失败: " + e.getMessage());
            return "redirect:/";
        }
    }

    @PostMapping("/task/{id}/cancel")
    public String cancelTask(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            analysisService.cancelTask(id);
            redirectAttributes.addFlashAttribute("message", "任务已成功中止");
            return "redirect:/";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "中止任务失败: " + e.getMessage());
            return "redirect:/task/" + id;
        }
    }

    @GetMapping("/task/new")
    public String newTaskForm() {
        return "task/new";
    }

    @PostMapping("/upload")
    public String uploadFiles(
            @RequestParam("taskName") String taskName,
            @RequestParam("wordFiles") MultipartFile[] wordFiles,
            @RequestParam("excelFiles") MultipartFile[] excelFiles,
            RedirectAttributes redirectAttributes) {

        try {
            // 保存上传的文件
            List<String> wordFilePaths = new ArrayList<>();
            List<String> excelFilePaths = new ArrayList<>();

            for (MultipartFile file : wordFiles) {
                if (!file.isEmpty()) {
                    String path = documentService.saveWordDocument(file);
                    wordFilePaths.add(path);
                }
            }

            for (MultipartFile file : excelFiles) {
                if (!file.isEmpty()) {
                    String path = documentService.saveExcelDocument(file);
                    excelFilePaths.add(path);
                }
            }

            // 创建分析任务
            AnalysisTask task = analysisService.createTask(taskName, wordFilePaths, excelFilePaths);

            // 启动异步分析
            CompletableFuture<AnalysisResult> future = analysisService.executeAnalysisTask(task.getId());

            redirectAttributes.addFlashAttribute("message", 
                    "任务创建成功，ID: " + task.getId() + "。分析正在后台进行，请稍后查看结果。");
            return "redirect:/task/" + task.getId();

        } catch (Exception e) {
            logger.error("文件上传失败", e);
            redirectAttributes.addFlashAttribute("error", "文件上传失败: " + e.getMessage());
            return "redirect:/new-task";
        }
    }

    @GetMapping("/api/tasks")
    @ResponseBody
    public ResponseEntity<List<AnalysisTask>> getAllTasks() {
        List<AnalysisTask> tasks = analysisService.getAllTasks();
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/api/task/{id}")
    @ResponseBody
    public ResponseEntity<?> getTaskById(@PathVariable String id) {
        try {
            AnalysisTask task = analysisService.getTaskById(id);
            return ResponseEntity.ok(task);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/task/{id}/result")
    @ResponseBody
    public ResponseEntity<?> getTaskResult(@PathVariable String id) {
        try {
            AnalysisResult result = analysisService.getResultByTaskId(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/tasks")
    @ResponseBody
    public ResponseEntity<?> createTask(
            @RequestParam("taskName") String taskName,
            @RequestParam("wordFiles") MultipartFile[] wordFiles,
            @RequestParam("excelFiles") MultipartFile[] excelFiles) {

        try {
            logger.info("接收到新任务请求: {}", taskName);
            logger.info("Word文件数量: {}, Excel文件数量: {}", 
                    wordFiles != null ? wordFiles.length : 0, 
                    excelFiles != null ? excelFiles.length : 0);
            
            // 保存上传的文件
            List<String> wordFilePaths = new ArrayList<>();
            List<String> excelFilePaths = new ArrayList<>();

            for (MultipartFile file : wordFiles) {
                if (!file.isEmpty()) {
                    String path = documentService.saveWordDocument(file);
                    wordFilePaths.add(path);
                    logger.info("保存Word文件: {}", file.getOriginalFilename());
                }
            }

            for (MultipartFile file : excelFiles) {
                if (!file.isEmpty()) {
                    String path = documentService.saveExcelDocument(file);
                    excelFilePaths.add(path);
                    logger.info("保存Excel文件: {}", file.getOriginalFilename());
                }
            }

            // 创建分析任务
            AnalysisTask task = analysisService.createTask(taskName, wordFilePaths, excelFilePaths);
            logger.info("任务创建成功: {}", task.getId());

            // 启动异步分析
            CompletableFuture<AnalysisResult> future = analysisService.executeAnalysisTask(task.getId());
            logger.info("异步分析任务已启动");

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "taskId", task.getId(),
                            "status", task.getStatus().name(),
                            "message", "分析任务已创建并开始处理"
                    ));

        } catch (Exception e) {
            logger.error("API创建任务失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/task/{id}/status")
    @ResponseBody
    public ResponseEntity<?> getTaskStatus(@PathVariable String id) {
        try {
            AnalysisTask task = analysisService.getTaskById(id);
            Map<String, Object> status = new HashMap<>();
            
            status.put("id", task.getId());
            status.put("taskName", task.getTaskName());
            status.put("status", task.getStatus().name());
            status.put("createdTime", task.getCreatedTime());
            
            // 获取任务进度信息
            Map<String, Object> progress = analysisService.getTaskProgress(id);
            status.put("progress", progress);
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/analysis/start")
    public ResponseEntity<Map<String, Object>> startAnalysis(
            @RequestBody Map<String, Object> request) {
        try {
            String taskId = (String) request.get("taskId");
            @SuppressWarnings("unchecked")
            List<String> selectedCategories = (List<String>) request.get("categories");
            
            if (taskId == null || selectedCategories == null) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Missing required parameters: taskId and categories")
                );
            }
            
            CompletableFuture<AnalysisResult> future = analysisService.startAnalysis(taskId, selectedCategories);
            
            return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "status", "processing",
                "message", "Analysis started"
            ));
            
        } catch (Exception e) {
            logger.error("启动分析失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/analysis/progress/{taskId}")
    public ResponseEntity<Map<String, Object>> getProgress(@PathVariable String taskId) {
        try {
            Map<String, Object> progress = analysisService.getTaskProgress(taskId);
            return ResponseEntity.ok(progress);
        } catch (Exception e) {
            logger.error("获取进度失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/task/{id}")
    @ResponseBody
    public ResponseEntity<?> apiDeleteTask(@PathVariable String id) {
        try {
            analysisService.deleteTask(id);
            return ResponseEntity.ok(Map.of("message", "任务已成功删除"));
        } catch (Exception e) {
            logger.error("API删除任务失败: " + id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
} 