'use strict';
const form=document.querySelector('#runForm');
const $=s=>document.querySelector(s);
let config, currentRun, polling, launching=false;
let screenshotSignature='';
let scheduleSignature='';
const terminal=s=>['SUCCEEDED','FAILED','CANCELLED'].includes(s);
const finished=r=>terminal(r.status)&&!r.notificationPending;
const runButtons=disabled=>{for(const id of ['#runButton','#deleteOnlyButton'])$(id).disabled=disabled;};
const setText=(selector,text)=>{$(selector).textContent=text??'';};
function otpVisibility(){const fixed=form.elements.otpMode.value==='FIXED'; $('#fixedOtpLabel').hidden=!fixed; form.elements.fixedOtp.required=fixed; form.elements.fixedOtp.disabled=!fixed;}
function defaults(){for(const [key,value] of Object.entries(config.defaults))if(form.elements[key])form.elements[key].value=value??''; otpVisibility();}
const savedFields=['mobile','dependency','name','ifsc','accountNumber','confirmAccountNumber','accountType','otpMode','fixedOtp','browserMode','scheduledLocalTime','notificationEmails'];
function notificationRecipients(){
 const emails=form.elements.notificationEmails.value.split(/[,;\r\n]+/).map(s=>s.trim()).filter(Boolean);
 if(emails.length>20||emails.some(e=>e.length>254||!/^([^\s,;<>@]+)@([^\s,;<>@]+)$/.test(e))){setText('#formError','Enter up to 20 valid notification email addresses.');return null;}
 return [...new Map(emails.map(e=>[e.toLowerCase(),e])).values()];
}
function applySaved(values){for(const [key,value] of Object.entries(values))if(savedFields.includes(key))form.elements[key].value=value;form.elements.confirmAccountNumber.setCustomValidity('');otpVisibility();}
let autoSaveTimer, draftVersion=0, persistenceQueue=Promise.resolve();
async function persistDetails(showMessage=true){
 if(notificationRecipients()===null)throw new Error('Please correct notification email addresses before saving.');
 const values=Object.fromEntries(savedFields.map(key=>[key,form.elements[key].value]));
 const pending=persistenceQueue.catch(()=>{}).then(()=>api('/api/saved-details',{method:'PUT',headers:scheduleHeaders(),body:JSON.stringify(values)}));
 persistenceQueue=pending;await pending;
 if(showMessage)setText('#savedDetailsMessage','Details and notification addresses saved on this computer. They remain until you click Clear saved details.');
}
function autoSaveDetails(delay=350){
 clearTimeout(autoSaveTimer);
 const version=++draftVersion;setText('#savedDetailsMessage','Saving details…');
 autoSaveTimer=setTimeout(async()=>{try{await persistDetails(false);if(version===draftVersion)setText('#savedDetailsMessage','Details saved automatically. They remain until you click Clear saved details.');}catch(e){/* Keep the last valid encrypted draft while a field, usually email, is incomplete. */}},delay);
}
for(const field of form.elements)if(field.name&&savedFields.includes(field.name)){
 field.addEventListener('input',()=>autoSaveDetails());
 field.addEventListener('change',()=>autoSaveDetails(0));
}
$('#saveDetails').addEventListener('click',async()=>{try{setText('#formError','');await persistDetails();}catch(e){setText('#formError',e.message);}});
$('#clearDetails').addEventListener('click',async()=>{try{
 clearTimeout(autoSaveTimer);
 const values=await api('/api/saved-details',{method:'DELETE',headers:scheduleHeaders()});applySaved(values);
 setText('#savedDetailsMessage','Saved details and email addresses cleared. Existing schedules keep their own saved inputs.');
}catch(e){setText('#formError',e.message);}});
async function api(path,options){const response=await fetch(path,{cache:'no-store',...options}); const data=await response.json(); if(!response.ok)throw new Error([data.error,...(data.details??[])].join('\n'));return data;}
function render(run){currentRun=run;setText('#status',run.status);setText('#step',run.step);setText('#runId',`Run ${run.id} · ${run.startedAt}`);setText('#runError',run.error);setText('#emailOutcome',run.notificationPending?'Preparing notification after this run':(run.diagnostics?.emailNotification?.message??''));$('#download').disabled=false;
 const shots=run.screenshots??[];const signature=shots.map(s=>s.url).join('|');$('#screenshotsPanel').hidden=!shots.length;
 setText('#resultPageUrl',run.diagnostics?.resultPage?.url?`Resulting page: ${run.diagnostics.resultPage.url}`:'');
 if(signature!==screenshotSignature){screenshotSignature=signature;$('#screenshotGallery').replaceChildren(...shots.map(s=>{const figure=document.createElement('figure');const caption=document.createElement('figcaption');caption.textContent=({'bank-popup':'Bank popup when displayed','bank-popup-before-proceed':'Bank popup immediately before Proceed','result-page':'Page after popup Proceed','admin-matched-entry':'Matched maker-checker entry','admin-delete-confirmation':'Deletion reason before Confirm Delete','admin-delete-success':'Admin deletion success popup','failure':'Failure screenshot'})[s.label]??s.label;const link=document.createElement('a');link.href=s.url;link.target='_blank';link.rel='noopener';const img=document.createElement('img');img.src=s.url;img.alt=caption.textContent;link.append(img);figure.append(caption,link);return figure;}));}
 setText('#workflowStage',run.kind==='DELETE_ONLY'?`Delete only · ${run.workflowStage??''}`:`${run.workflowStage??''} · OTP entered: ${Boolean(run.otpEntered)} · Submitted: ${Boolean(run.otpSubmitted)}`);
 $('#cancel').disabled=finished(run)||run.cancelRequested||run.workflowStage==='COMPLETED';
 const screenshot=run.diagnostics?.screenshot?.url;$('#screenshotLink').hidden=!screenshot;if(screenshot)$('#screenshotLink').href=screenshot;
 $('#diagnosticPanel').hidden=!Object.keys(run.diagnostics??{}).length;setText('#diagnostics',JSON.stringify(run.diagnostics??{},null,2));
 $('#logs').replaceChildren(...run.logs.map(log=>{const li=document.createElement('li');li.textContent=`${new Date(log.time).toLocaleTimeString()} · ${log.workflowStage??''} · ${log.message}`;return li;}));
 setText('#requestCount',`${run.network.length} request hops`);
 $('#networkBody').replaceChildren(...run.network.map(n=>{const tr=document.createElement('tr');const values=[`${n.requestId} / ${n.hop}`,n.step,`${n.method} ${n.url}`,`${n.status??'—'} / ${n.wireStatus??'—'}`,`${n.state}${n.error?' · '+n.error:''}`,`${n.cached?'Cached':'Network'}${n.serviceWorker?' · service worker':''}`];for(const value of values){const td=document.createElement('td');td.textContent=value;tr.append(td);}return tr;}));
}
async function refreshHistory(){const runs=await api('/api/runs');const selected=$('#history').value;$('#history').replaceChildren(new Option('Select a run',''),...runs.slice().reverse().map(r=>new Option(`${r.startedAt} · ${r.status}`,r.id)));$('#history').value=selected;return runs;}
async function watch(id){clearTimeout(polling);try{const run=await api(`/api/runs/${encodeURIComponent(id)}`);render(run);runButtons(!finished(run));if(!finished(run))polling=setTimeout(()=>watch(id),700);else await refreshHistory();}catch(e){setText('#runError',`Monitoring interrupted: ${e.message}. Reload to reconnect to the active run.`);}}
form.elements.otpMode.addEventListener('change',otpVisibility);
form.elements.ifsc.addEventListener('input',()=>{form.elements.ifsc.value=form.elements.ifsc.value.toUpperCase();});
$('#resetDefaults').addEventListener('click',defaults);
function journeyInput(){setText('#formError','');
 const a=form.elements.accountNumber,b=form.elements.confirmAccountNumber;b.setCustomValidity(a.value===b.value?'':'Account numbers must match');
 if(!['RANDOM','FIXED'].includes(form.elements.otpMode.value)||!['VISIBLE','HEADLESS'].includes(form.elements.browserMode.value)){setText('#formError','Invalid OTP or browser mode');return;}
 if(!form.reportValidity())return;const input=Object.fromEntries(new FormData(form));delete input.scheduledLocalTime;const emails=notificationRecipients();if(emails===null)return;input.notificationEmails=emails;if(input.otpMode==='RANDOM')input.fixedOtp=null;
 if(['mobile','dependency','name','ifsc','accountNumber','confirmAccountNumber','accountType'].some(key=>!input[key]?.trim())){setText('#formError','All required fields must contain a non-blank value.');return;}
 return input;
}
async function launch(path,input){
 launching=true;runButtons(true);
 try{await persistDetails();const run=await api(path,{method:'POST',headers:{'Content-Type':'application/json','X-Portal-Token':config.runToken},body:JSON.stringify(input)});await refreshHistory();$('#history').value=run.id;await watch(run.id);}catch(e){setText('#formError',e.message);runButtons(false);}finally{launching=false;}
}
form.addEventListener('submit',async event=>{event.preventDefault();if(launching)return;const input=journeyInput();if(input)await launch('/api/runs',input);});
$('#deleteOnlyButton').addEventListener('click',async()=>{
 if(launching)return;setText('#formError','');const input={};
 for(const key of ['mobile','name','ifsc','accountNumber','browserMode']){const field=form.elements[key];if(!field.value.trim()){setText('#formError',`Deletion requires ${key}.`);field.focus();return;}if(!field.reportValidity())return;input[key]=field.value;}
 if(!['VISIBLE','HEADLESS'].includes(input.browserMode)){setText('#formError','Invalid browser mode');return;}
 const emails=notificationRecipients();if(emails===null)return;input.notificationEmails=emails;
 await launch('/api/runs/delete-only',input);
});
const scheduleHeaders=()=>({'Content-Type':'application/json','X-Portal-Token':config.runToken});
$('#scheduleButton').addEventListener('click',async()=>{
 const input=journeyInput();if(!input)return;const scheduledLocalTime=form.elements.scheduledLocalTime.value;
 if(!scheduledLocalTime||new Date(scheduledLocalTime+'+05:30').getTime()<=Date.now()){setText('#formError','Choose a future date and time in IST.');return;}
 $('#scheduleButton').disabled=true;
 try{await persistDetails();await api('/api/schedules',{method:'POST',headers:scheduleHeaders(),body:JSON.stringify({input,scheduledLocalTime})});setText('#scheduleMessage','Saved. This schedule will use the form values saved now.');await refreshSchedules();}catch(e){setText('#formError',e.message);}finally{$('#scheduleButton').disabled=false;}
});
async function scheduleAction(s,action){try{
 if(action==='load'){const saved=await api(`/api/schedules/${s.id}/inputs`,{headers:scheduleHeaders()});for(const [key,value] of Object.entries(saved.input))if(form.elements[key])form.elements[key].value=Array.isArray(value)?value.join(', '):(value??'');form.elements.scheduledLocalTime.value=saved.scheduledLocalTime;otpVisibility();await persistDetails(false);setText('#scheduleMessage','Saved inputs loaded and retained in the form. Changing the form does not change an existing schedule.');form.scrollIntoView({behavior:'smooth'});}
 else if(action==='report'){const report=await api(`/api/schedules/${s.id}/report`);clearTimeout(polling);render(report);if(!terminal(report.status))watch(report.id);}
 else{await api(`/api/schedules/${s.id}${action==='cancel'?'/cancel':''}`,{method:action==='cancel'?'POST':'DELETE',headers:scheduleHeaders()});await refreshSchedules();}
 }catch(e){setText('#scheduleError',e.message);}}
async function refreshSchedules(){
 const [items,health]=await Promise.all([api('/api/schedules'),api('/api/scheduler')]);
 setText('#schedulerClock',`${new Date(health.currentTime).toLocaleString('en-IN',{timeZone:'Asia/Kolkata'})} IST${health.enabled?'':' — Scheduler disabled'}`);setText('#scheduleError',health.error);
 const signature=JSON.stringify(items);if(signature!==scheduleSignature){scheduleSignature=signature;
 $('#scheduleBody').replaceChildren(...items.map(s=>{const row=document.createElement('tr');for(const value of [s.scheduledLocalTime.replace('T',' ')+' IST',`${s.name} · Mobile ${s.mobileMasked} · Account ${s.accountMasked}`,s.status+(s.message?' · '+s.message:'')]){const td=document.createElement('td');td.textContent=value;row.append(td);}const td=document.createElement('td');for(const [label,action] of [['Load inputs','load'],...(s.runId?[['View run','report']]:[]),...(['PENDING','WAITING_FOR_RUNNER'].includes(s.status)?[['Cancel schedule','cancel']]:[]),...(!['RUNNING','DISPATCHING'].includes(s.status)?[['Delete','delete']]:[])]){const button=document.createElement('button');button.type='button';button.className='secondary';button.textContent=label;button.addEventListener('click',()=>scheduleAction(s,action));td.append(button);}row.append(td);return row;}));}
 const active=items.find(s=>s.status==='RUNNING'&&s.runId);if(active&&(!currentRun||finished(currentRun))&&currentRun?.id!==active.runId)watch(active.runId);
}
async function schedulePoll(){try{await refreshSchedules();}catch(e){setText('#scheduleError',`Scheduler monitoring interrupted: ${e.message}`);}setTimeout(schedulePoll,2000);}
form.elements.confirmAccountNumber.addEventListener('input',()=>form.elements.confirmAccountNumber.setCustomValidity(''));
form.elements.accountNumber.addEventListener('input',()=>form.elements.confirmAccountNumber.setCustomValidity(''));
$('#history').addEventListener('change',async()=>{const runs=await api('/api/runs');const active=runs.find(r=>!finished(r));if(active){$('#history').value=active.id;watch(active.id);}else if($('#history').value)watch($('#history').value);});
$('#download').addEventListener('click',()=>{if(!currentRun)return;const url=URL.createObjectURL(new Blob([JSON.stringify(currentRun,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download=`kyc-run-${currentRun.id}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});
$('#cancel').addEventListener('click',async()=>{if(!currentRun||finished(currentRun))return;$('#cancel').disabled=true;try{await api(`/api/runs/${encodeURIComponent(currentRun.id)}/cancel`,{method:'POST',headers:{'X-Portal-Token':config.runToken}});await watch(currentRun.id);}catch(e){setText('#runError',e.message);}});
(async()=>{try{config=await api('/api/config');defaults();const saved=await api('/api/saved-details',{headers:scheduleHeaders()});applySaved(saved.values);setText('#emailSetup',saved.emailConfigured?'Email sender configured. Notifications for successful, failed and cancelled runs use the recipients submitted for each run.':'Email delivery needs SMTP setup on this computer. Addresses can be saved now; runs will report NOT_CONFIGURED until a sender is configured.');$("#saveDetails").disabled=false;$("#clearDetails").disabled=false;$('#scheduleButton').disabled=false;setText('#locatorWarning',config.locatorWarning);setText('#cleanupNotice',config.adminCleanupEnabled?'After result capture, waits 10 seconds, signs into maker-checker and deletes only the matched Pennydrop Only / Verify entry using Duplicate entry. Admin API responses and screenshots appear in this monitor.':'Admin cleanup is disabled.');const runs=await refreshHistory();const active=runs.find(r=>!finished(r));if(active){$('#history').value=active.id;watch(active.id);}else runButtons(false);schedulePoll();}catch(e){setText('#formError',`Portal initialization failed: ${e.message}`);}})();


