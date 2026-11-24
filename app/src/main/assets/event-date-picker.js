'use strict';
// Keep the original date and repeat fields as the persisted format and public editor API.
const editEventBeforeQuickDate=editEvent;
editEvent=function(id){
 editEventBeforeQuickDate(id);
 const date=$('eventDate'),repeat=$('eventRepeat'),category=$('eventCategory'),row=data.events.find(e=>e.id===id);
 if(!date||!repeat)return;
 const birthdayOption=document.createElement('option');birthdayOption.value='生日';birthdayOption.textContent='生日';category.append(birthdayOption);
 if(row?.category&&!Array.from(category.options).some(o=>o.value===row.category)){const option=document.createElement('option');option.value=row.category;option.textContent=row.category;category.append(option);}
 if(row?.category)category.value=row.category;
 const dateLabel=$('sheetBody').querySelector('label[for="eventDate"]');dateLabel.htmlFor='eventYear';dateLabel.textContent='日期 · 年月日直接选择';
 date.hidden=true;date.classList.add('event-canonical-date');date.setAttribute('aria-hidden','true');date.tabIndex=-1;
 const templates=document.createElement('div');templates.className='event-date-templates';templates.setAttribute('role','group');templates.setAttribute('aria-label','倒计日重复方式');
 templates.innerHTML='<button type="button" id="eventSingle" aria-pressed="false"><b>单次倒计日</b><small>只记录这一次</small></button><button type="button" id="eventYearly" aria-pressed="false"><b>每年重复</b><small>生日、周年、节日等都适用</small></button>';
 dateLabel.before(templates);
 const picker=document.createElement('div');picker.className='event-date-picker';picker.id='eventDatePicker';
 picker.innerHTML=`<div class="event-date-parts"><label><span>年份</span><input class="field" id="eventYear" type="number" inputmode="numeric" min="1900" max="2200" step="1" aria-label="年份，1900至2200"></label><label><span>月份</span><select class="field" id="eventMonth" aria-label="月份">${Array.from({length:12},(_,i)=>`<option value="${i+1}">${i+1} 月</option>`).join('')}</select></label><label><span>日期</span><select class="field" id="eventDay" aria-label="日期"></select></label></div><div class="event-date-shortcuts" role="group" aria-label="快捷日期"><button type="button" data-event-days="0">今天</button><button type="button" data-event-days="1">明天</button><button type="button" data-event-days="7">一周后</button><button type="button" data-event-days="30">30 天后</button></div><p id="eventDateError" class="event-date-error" role="status" hidden></p>`;
 date.after(picker);
 const repeatLabel=$('sheetBody').querySelector('label[for="eventRepeat"]');repeatLabel.textContent='重复方式';
 repeat.querySelector('option[value="yearly"]').textContent='每年重复';
 const details=document.createElement('div');details.className='event-repeat-settings';details.append(repeatLabel,repeat);
 const preview=document.createElement('p');preview.id='eventRepeatPreview';preview.className='event-repeat-preview';preview.setAttribute('aria-live','polite');details.append(preview);picker.after(details);
 let selectedDay='',badDay='';
 function leap(year){return year%4===0&&(year%100!==0||year%400===0);}
 function monthDays(year,month){return month===2?(leap(year)?29:28):[4,6,9,11].includes(month)?30:31;}
 function validYear(){const year=Number($('eventYear').value);return $('eventYear').value!==''&&Number.isInteger(year)&&year>=1900&&year<=2200;}
 function updateDayOptions(){
  const max=validYear()?monthDays(Number($('eventYear').value),Number($('eventMonth').value)):31;
  if(selectedDay&&Number(selectedDay)>max){badDay=selectedDay;selectedDay='';}
  else if(!selectedDay&&badDay&&validYear()&&Number(badDay)<=max){selectedDay=badDay;badDay='';}
  $('eventDay').innerHTML='<option value="">请选择</option>'+Array.from({length:max},(_,i)=>`<option value="${i+1}">${i+1} 日</option>`).join('');$('eventDay').value=selectedDay;
 }
 function setError(message){$('eventDateError').textContent=message;$('eventDateError').hidden=!message;$('eventYear').setAttribute('aria-invalid',String(!validYear()));$('eventDay').setAttribute('aria-invalid',String(!selectedDay));}
 function describe(){
  $('eventSingle').setAttribute('aria-pressed',String(repeat.value==='none'));$('eventYearly').setAttribute('aria-pressed',String(repeat.value==='yearly'));
  if(!date.value||!date.checkValidity()){preview.textContent='先选好有效日期，再计算下一次。';return;}
  const next=LumiFeaturesData.occurrence({date:date.value,repeat:repeat.value},localDay()).replaceAll('-',' / ');
  if(repeat.value==='yearly')preview.textContent=`每年按所选月日重复；下一次：${next}。${date.value.slice(5)==='02-29'?'2 月 29 日在平年按 2 月 28 日计算，闰年恢复 2 月 29 日。':'过了当天会自动计算下一年。'}`;
  else if(repeat.value==='monthly')preview.textContent=`每月重复；下一次：${next}。小月没有所选日期时，按当月最后一天计算。`;
  else if(repeat.value==='weekly')preview.textContent=`每周重复；下一次：${next}。`;
  else preview.textContent=`只记录这一次：${next}。需要每年记录的日期可以选择「每年重复」。`;
 }
 function writeDate(){
  updateDayOptions();let error='';
  if(!validYear())error='请输入 1900 至 2200 年之间的完整年份';
  else if(!selectedDay)error=badDay?`这个月没有 ${badDay} 日，请重新选择日期`:'请选择有效日期';
  date.value=error?'':`${Number($('eventYear').value)}-${String($('eventMonth').value).padStart(2,'0')}-${String(selectedDay).padStart(2,'0')}`;setError(error);describe();
 }
 function readDate(){
  if(!date.value||!date.checkValidity()){setError('请选择有效日期');describe();return;}
  const [year,month,day]=date.value.split('-').map(Number);$('eventYear').value=year;$('eventMonth').value=month;selectedDay=String(day);badDay='';updateDayOptions();setError('');describe();
 }
 $('eventYear').oninput=writeDate;$('eventYear').onchange=writeDate;$('eventMonth').onchange=writeDate;
 $('eventDay').onchange=()=>{selectedDay=$('eventDay').value;badDay='';writeDate();};
 date.addEventListener('input',readDate);date.addEventListener('change',readDate);
 picker.querySelectorAll('[data-event-days]').forEach(button=>button.onclick=()=>{date.value=LumiFeaturesData.addDays(localDay(),Number(button.dataset.eventDays));readDate();});
 $('eventYearly').onclick=()=>{repeat.value='yearly';describe();};
 $('eventSingle').onclick=()=>{repeat.value='none';describe();};
 repeat.addEventListener('change',describe);category.addEventListener('change',describe);
 const saveBeforeQuickDate=$('saveEvent').onclick;
 $('saveEvent').onclick=function(event){
  if(!date.value||!date.checkValidity()){$('formError').textContent=$('eventDateError').textContent||'请选择 1900 至 2200 年之间的有效日期';if(!validYear())$('eventYear').focus();else $('eventDay').focus();return;}
  saveBeforeQuickDate.call(this,event);
 };
 readDate();
};
