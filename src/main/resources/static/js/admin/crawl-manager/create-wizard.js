/**
 * Crawl Manager - Create Job Wizard
 * 4-step wizard for creating new crawl jobs
 */

import {apolloClient, gql} from '../graphql/apollo-client.js';
import {refreshSidebar} from './sidebar.js';
import {showJobDetail} from './detail-panel.js';

// State
let currentStep = 1;
let wizardData = {
    crawlType: 'COMIC',
    targetUrl: '',
    downloadMode: 'FULL',
    parallelLimit: 3,
    imageQuality: 85,
    timeoutSeconds: 30,
    rangeStart: 0,
    rangeEnd: -1
};
let duplicateInfo = null;
let checkDuplicateTimeout = null;

// GraphQL Mutations
const CREATE_CRAWL_JOB = gql`
    mutation CreateCrawlJob($input: CreateCrawlJobInput!) {
        createCrawlJob(input: $input) {
            id
            type
            status
            targetUrl
        }
    }
`;

// Initialize wizard
export function initWizard() {
    // URL input change handler
    document.getElementById('wizardUrl')?.addEventListener('input', (e) => {
        wizardData.targetUrl = e.target.value;

        // Debounced duplicate check
        clearTimeout(checkDuplicateTimeout);
        checkDuplicateTimeout = setTimeout(() => {
            checkDuplicate(e.target.value);
        }, 500);
    });

    // Download mode change
    document.querySelectorAll('.mode-option').forEach(option => {
        option.addEventListener('click', () => {
            const mode = option.dataset.mode;
            selectDownloadMode(mode);
        });
    });

    // Settings inputs
    document.getElementById('settingParallel')?.addEventListener('change', (e) => {
        wizardData.parallelLimit = parseInt(e.target.value);
    });
    document.getElementById('settingTimeout')?.addEventListener('change', (e) => {
        wizardData.timeoutSeconds = parseInt(e.target.value);
    });
    document.getElementById('settingQuality')?.addEventListener('change', (e) => {
        wizardData.imageQuality = parseInt(e.target.value);
    });
    document.getElementById('settingMaxWidth')?.addEventListener('change', (e) => {
        wizardData.maxImageWidth = parseInt(e.target.value) || 0;
    });
    document.getElementById('settingRangeStart')?.addEventListener('change', (e) => {
        wizardData.rangeStart = parseInt(e.target.value) || 0;
    });
    document.getElementById('settingRangeEnd')?.addEventListener('change', (e) => {
        wizardData.rangeEnd = parseInt(e.target.value);
    });
}

// Open wizard
export function openWizard() {
    resetWizard();
    document.getElementById('wizardBackdrop').classList.add('open');
    document.getElementById('wizardDrawer').classList.add('open');
}

// Close wizard
export function closeWizard() {
    document.getElementById('wizardBackdrop').classList.remove('open');
    document.getElementById('wizardDrawer').classList.remove('open');
}

// Reset wizard
function resetWizard() {
    currentStep = 1;
    wizardData = {
        crawlType: 'COMIC',
        targetUrl: '',
        downloadMode: 'FULL',
        parallelLimit: 3,
        imageQuality: 85,
        timeoutSeconds: 30,
        rangeStart: 0,
        rangeEnd: -1
    };
    duplicateInfo = null;

    // Reset UI
    showStep(1);
    document.getElementById('wizardUrl').value = '';
    document.getElementById('duplicateWarning').style.display = 'none';

    // Reset type selection
    document.querySelectorAll('.type-card').forEach(card => {
        card.classList.toggle('selected', card.dataset.type === 'COMIC');
    });

    // Reset mode selection
    selectDownloadMode('FULL');

    // Reset settings
    document.getElementById('settingParallel').value = '3';
    document.getElementById('settingTimeout').value = '30';
    document.getElementById('settingCompress').checked = true;
    document.getElementById('settingQuality').value = '85';
    document.getElementById('settingMaxWidth').value = '0';
    document.getElementById('settingRangeStart').value = '0';
    document.getElementById('settingRangeEnd').value = '-1';
}

// Select crawl type
export function selectType(type) {
    wizardData.crawlType = type;

    document.querySelectorAll('.type-card').forEach(card => {
        card.classList.toggle('selected', card.dataset.type === type);
    });
}

// Select download mode
function selectDownloadMode(mode) {
    wizardData.downloadMode = mode;

    document.querySelectorAll('.mode-option').forEach(option => {
        option.classList.toggle('selected', option.dataset.mode === mode);
        option.querySelector('input').checked = option.dataset.mode === mode;
    });
}

// Check for duplicate - disabled as backend handles this automatically
async function checkDuplicate(url) {
    // Backend will auto-detect duplicates and adjust download mode
    // No need for separate check - just hide warning
    document.getElementById('duplicateWarning').style.display = 'none';
    duplicateInfo = null;
}

// Navigate to next step
export function wizardNext() {
    if (currentStep >= 4) return;

    // Validate current step
    if (!validateStep(currentStep)) return;

    currentStep++;
    showStep(currentStep);

    // If going to step 4, update summary
    if (currentStep === 4) {
        updateSummary();
    }
}

// Navigate to previous step
export function wizardBack() {
    if (currentStep <= 1) return;
    currentStep--;
    showStep(currentStep);
}

// Show specific step
function showStep(step) {
    // Hide all steps
    document.querySelectorAll('.wizard-step').forEach(s => {
        s.style.display = 'none';
    });

    // Show current step
    document.getElementById(`wizardStep${step}`).style.display = 'block';

    // Update step indicator
    document.getElementById('currentStep').textContent = step;

    // Update buttons
    document.getElementById('wizardBackBtn').style.display = step > 1 ? 'inline-flex' : 'none';
    document.getElementById('wizardNextBtn').style.display = step < 4 ? 'inline-flex' : 'none';
    document.getElementById('wizardStartBtn').style.display = step === 4 ? 'inline-flex' : 'none';

    // Update feather icons
    if (typeof feather !== 'undefined') {
        feather.replace();
    }
}

// Validate step
function validateStep(step) {
    switch (step) {
        case 1:
            if (!wizardData.crawlType) {
                alert('Please select a crawl type');
                return false;
            }
            return true;

        case 2:
            if (!wizardData.targetUrl) {
                alert('Please enter a URL');
                return false;
            }
            try {
                new URL(wizardData.targetUrl);
            } catch {
                alert('Please enter a valid URL');
                return false;
            }
            return true;

        case 3:
            // Settings are optional, always valid
            return true;

        default:
            return true;
    }
}

// Update summary
function updateSummary() {
    document.getElementById('summaryType').textContent = wizardData.crawlType;
    document.getElementById('summaryUrl').textContent = wizardData.targetUrl;
    document.getElementById('summaryMode').textContent = wizardData.downloadMode;
    document.getElementById('summaryParallel').textContent = `${wizardData.parallelLimit} downloads`;
    document.getElementById('summaryQuality').textContent = `${wizardData.imageQuality}%`;

    const rangeText = wizardData.rangeEnd === -1
        ? `From ${wizardData.rangeStart} to end`
        : `From ${wizardData.rangeStart} to ${wizardData.rangeEnd}`;
    document.getElementById('summaryRange').textContent = wizardData.rangeStart === 0 && wizardData.rangeEnd === -1
        ? 'All items'
        : rangeText;
}

// Reset settings to defaults
window.resetSettings = function () {
    document.getElementById('settingParallel').value = '3';
    document.getElementById('settingTimeout').value = '30';
    document.getElementById('settingCompress').checked = true;
    document.getElementById('settingQuality').value = '85';
    document.getElementById('settingMaxWidth').value = '0';
    document.getElementById('settingRangeStart').value = '0';
    document.getElementById('settingRangeEnd').value = '-1';

    wizardData.parallelLimit = 3;
    wizardData.timeoutSeconds = 30;
    wizardData.imageQuality = 85;
    wizardData.rangeStart = 0;
    wizardData.rangeEnd = -1;
};

// Start crawl
export async function startCrawl() {
    const startBtn = document.getElementById('wizardStartBtn');
    startBtn.disabled = true;
    startBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span> Creating...';

    try {
        const input = {
            type: wizardData.crawlType,
            targetUrl: wizardData.targetUrl,
            downloadMode: wizardData.downloadMode,
            settings: {
                parallelLimit: wizardData.parallelLimit,
                imageQuality: wizardData.imageQuality,
                timeoutSeconds: wizardData.timeoutSeconds,
                rangeStart: wizardData.rangeStart,
                rangeEnd: wizardData.rangeEnd
            }
        };

        const result = await apolloClient.mutate({
            mutation: CREATE_CRAWL_JOB,
            variables: {input}
        });

        console.log('[Wizard] Mutation response:', result);
        const data = result?.data;

        if (data?.createCrawlJob?.id) {
            closeWizard();
            refreshSidebar();
            showJobDetail(data.createCrawlJob.id);

            // Update URL
            history.pushState({jobId: data.createCrawlJob.id}, '', `/admin/crawl/${data.createCrawlJob.id}`);
        } else {
            throw new Error('Failed to create job');
        }
    } catch (error) {
        console.error('Error creating job:', error);
        alert('Failed to create crawl job: ' + (error.message || 'Unknown error'));
    } finally {
        startBtn.disabled = false;
        startBtn.innerHTML = '<i data-feather="play"></i> Start Crawl';
        if (typeof feather !== 'undefined') {
            feather.replace();
        }
    }
}

