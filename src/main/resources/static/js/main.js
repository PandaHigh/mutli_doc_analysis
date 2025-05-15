// 日期格式化函数
function formatDate(date) {
    return new Date(date).toLocaleString();
}

// 进度更新函数
function updateProgress(taskId, progress) {
    const progressBar = document.querySelector(`#task-${taskId} .progress-bar`);
    if (progressBar) {
        progressBar.style.width = `${progress}%`;
        progressBar.setAttribute('aria-valuenow', progress);
        progressBar.textContent = `${progress}%`;
    }
} 